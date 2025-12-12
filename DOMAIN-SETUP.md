# 도메인 연결 가이드 (sql2sql.kr)

## 1단계: DNS 설정

도메인 등록 사이트(가비아, 호스팅케이알 등)에서 DNS 설정:

```
타입: A
호스트: @
값: [EC2 서버 퍼블릭 IP]
TTL: 300

타입: A
호스트: www
값: [EC2 서버 퍼블릭 IP]
TTL: 300
```

DNS 전파까지 5분~48시간 소요 (보통 10분 이내)

확인:
```bash
nslookup sql2sql.kr
ping sql2sql.kr
```

---

## 2단계: 현재 설정으로 HTTP 배포

### 로컬에서:
```bash
# 1. 이미지 다시 빌드 (nginx.conf 변경사항 반영)
docker-compose build frontend

# 2. Docker Hub에 푸시
docker push jiin724/sql-converter-frontend:latest
```

### 서버에서:
```bash
# 1. 컨테이너 재시작
cd /home/ec2-user/backend
docker compose down
docker compose pull
docker compose up -d

# 2. 확인
curl http://sql2sql.kr
```

---

## 3단계: HTTPS 설정 (SSL 인증서)

### 서버에서 Let's Encrypt 인증서 발급:

```bash
# 1. Certbot 설치 (Amazon Linux 2)
sudo yum install -y certbot

# 2. 80번 포트 사용 중인 컨테이너 잠시 중지
docker compose down

# 3. SSL 인증서 발급
sudo certbot certonly --standalone -d sql2sql.kr -d www.sql2sql.kr

# 이메일 입력하고 약관 동의
# 성공하면 인증서가 /etc/letsencrypt/live/sql2sql.kr/ 에 저장됨

# 4. 인증서 디렉토리 확인
sudo ls -la /etc/letsencrypt/live/sql2sql.kr/
```

### Docker Compose 설정 업데이트:

`docker-compose.prod.yml`에서 frontend 볼륨 추가:

```yaml
  frontend:
    image: jiin724/sql-converter-frontend:latest
    container_name: sql-converter-frontend
    ports:
      - "80:80"
      - "443:443"
    depends_on:
      - backend
    networks:
      - sql-converter-network
    restart: always
    volumes:
      # SSL 인증서 마운트
      - /etc/letsencrypt:/etc/letsencrypt:ro
      # HTTPS 설정 적용
      - ./nginx-ssl.conf:/etc/nginx/conf.d/default.conf:ro
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:80"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 10s
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"
```

### 서버에 nginx-ssl.conf 복사:

```bash
# 로컬에서 서버로 복사
scp frontend/nginx-ssl.conf ec2-user@[서버IP]:/home/ec2-user/backend/
```

### 컨테이너 재시작:

```bash
# 서버에서
cd /home/ec2-user/backend
docker compose -f docker-compose.prod.yml down
docker compose -f docker-compose.prod.yml up -d

# 로그 확인
docker compose logs -f frontend
```

### 인증서 자동 갱신 설정:

```bash
# Cron job 추가
sudo crontab -e

# 매월 1일 새벽 2시에 인증서 갱신
0 2 1 * * certbot renew --quiet && docker compose -f /home/ec2-user/backend/docker-compose.prod.yml restart frontend
```

---

## 4단계: 방화벽 설정 (AWS Security Group)

AWS Console에서 EC2 Security Group 설정:

```
인바운드 규칙:
- Type: HTTP, Port: 80, Source: 0.0.0.0/0
- Type: HTTPS, Port: 443, Source: 0.0.0.0/0
- Type: Custom TCP, Port: 8080, Source: 0.0.0.0/0 (선택사항)
```

---

## 5단계: 테스트

```bash
# HTTP (HTTPS로 리다이렉트 확인)
curl -I http://sql2sql.kr

# HTTPS
curl -I https://sql2sql.kr

# 브라우저에서
https://sql2sql.kr
```

---

## 트러블슈팅

### DNS가 안 될 때:
```bash
# DNS 전파 확인
nslookup sql2sql.kr
dig sql2sql.kr

# 직접 IP로 테스트
curl http://[서버IP]
```

### SSL 인증서 발급 실패:
```bash
# 80번 포트가 비어있는지 확인
sudo netstat -tlnp | grep :80

# 방화벽 확인
sudo iptables -L
```

### nginx 설정 오류:
```bash
# 컨테이너 내부에서 nginx 설정 테스트
docker exec sql-converter-frontend nginx -t

# 로그 확인
docker logs sql-converter-frontend
```