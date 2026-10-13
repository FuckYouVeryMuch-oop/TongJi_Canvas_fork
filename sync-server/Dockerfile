FROM python:3.12-alpine
WORKDIR /app
COPY server.py .
USER 65534:65534
EXPOSE 8787
CMD ["python", "server.py"]
