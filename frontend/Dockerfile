# Build stage
FROM node:20-alpine AS builder

WORKDIR /app

# Install build dependencies for native modules
RUN apk add --no-cache python3 make g++

# Copy package files
COPY package*.json ./

# Install dependencies (optional deps needed for rollup native binaries)
RUN npm ci --prefer-offline --no-audit || npm install --prefer-offline --no-audit

# Copy source code
COPY . .

# Build the application (skip TypeScript check for Docker)
RUN npm run build:docker

# Production stage
FROM nginx:alpine

# Copy built files from builder stage
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Expose port 80
EXPOSE 80

# Start nginx
CMD ["nginx", "-g", "daemon off;"]