package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"os/exec"
	"regexp"
	"strings"
	"time"
)

const tokenPlaceholder = "PASTE_YOUR_TOKEN_HERE"

var userTokenPattern = regexp.MustCompile(`(?m)^\s*user_token\s*:\s*(?:"([^"]*)"|'([^']*)'|([^#\s]+))\s*(?:#.*)?$`)

// readStudentToken достаёт user_token из config.yml. Полноценный YAML-парсер тут
// избыточен: в файле ровно одно поле, а лишняя зависимость тянет go.sum.
func readStudentToken() (string, error) {
	data, err := os.ReadFile(studentConfigFile)
	if err != nil {
		return "", fmt.Errorf("в корне проекта нет файла %s с вашим токеном", studentConfigFile)
	}

	match := userTokenPattern.FindStringSubmatch(string(data))
	if match == nil {
		return "", fmt.Errorf("в %s не найдено поле user_token", studentConfigFile)
	}

	token := ""
	for _, group := range match[1:] {
		if group != "" {
			token = group
			break
		}
	}
	if strings.TrimSpace(token) == "" || token == tokenPlaceholder {
		return "", fmt.Errorf("в %s не заполнен user_token", studentConfigFile)
	}
	return token, nil
}

type callbackExtra struct {
	StudentRepo string `json:"student_repo"`
	StudentRef  string `json:"student_ref"`
}

type callbackRequest struct {
	Created   time.Time     `json:"created"`
	TaskID    string        `json:"task_id"`
	UserToken string        `json:"user_token"`
	Extra     callbackExtra `json:"extra"`
}

func submit(userToken string) error {
	body, err := json.Marshal(callbackRequest{
		Created:   time.Now(),
		TaskID:    callbackTaskID,
		UserToken: userToken,
		Extra: callbackExtra{
			StudentRepo: detect("GITHUB_REPOSITORY", "config", "--get", "remote.origin.url"),
			StudentRef:  detect("GITHUB_REF", "rev-parse", "--abbrev-ref", "HEAD"),
		},
	})
	if err != nil {
		return err
	}

	client := &http.Client{Timeout: 30 * time.Second}
	lastError := "попыток не было"

	for attempt := 1; attempt <= 3; attempt++ {
		status, reason := postOnce(client, body)
		switch {
		case status == http.StatusOK:
			return nil
		case status >= 500 || status == 0:
			lastError = reason
			logf("попытка %d/3 не удалась: %s", attempt, reason)
			if attempt < 3 {
				time.Sleep(time.Duration(attempt) * 2 * time.Second)
			}
		default:
			return fmt.Errorf("LMS отклонила результат: %s", reason)
		}
	}
	return fmt.Errorf("не удалось отправить результат за 3 попытки: %s", lastError)
}

func postOnce(client *http.Client, body []byte) (int, string) {
	request, err := http.NewRequest(http.MethodPost, lmsBaseURL, bytes.NewReader(body))
	if err != nil {
		return 0, err.Error()
	}
	request.Header.Set("Authorization", "Bearer "+lmsCompanyToken)
	request.Header.Set("Content-Type", "application/json")

	response, err := client.Do(request)
	if err != nil {
		return 0, "сетевая ошибка: " + err.Error()
	}
	defer response.Body.Close()

	if response.StatusCode == http.StatusOK {
		return http.StatusOK, ""
	}
	answer := make([]byte, 300)
	read, _ := response.Body.Read(answer)
	return response.StatusCode, fmt.Sprintf("статус %d: %s", response.StatusCode, answer[:read])
}

// detect берёт значение из переменной окружения GitHub Actions,
// а при локальном запуске откатывается на git.
func detect(envKey string, gitArgs ...string) string {
	if value := strings.TrimSpace(os.Getenv(envKey)); value != "" {
		return value
	}
	output, err := exec.Command("git", gitArgs...).Output()
	if value := strings.TrimSpace(string(output)); err == nil && value != "" {
		return value
	}
	return "local"
}
