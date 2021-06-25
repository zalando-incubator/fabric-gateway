TOK=eyJraWQiOiJwbGF0Zm9ybS1pYW0tdmNlaHloajYiLCJhbGciOiJFUzI1NiJ9.eyJzdWIiOiI5NTM4ZDZkYS0wYjY1LTQ3NGMtYjFlOC00Mzg5N2JkOGZiNjUiLCJodHRwczovL2lkZW50aXR5LnphbGFuZG8uY29tL3JlYWxtIjoidXNlcnMiLCJodHRwczovL2lkZW50aXR5LnphbGFuZG8uY29tL3Rva2VuIjoiQmVhcmVyIiwiaHR0cHM6Ly9pZGVudGl0eS56YWxhbmRvLmNvbS9tYW5hZ2VkLWlkIjoiYm1vb25leSIsImF6cCI6Inp0b2tlbiIsImh0dHBzOi8vaWRlbnRpdHkuemFsYW5kby5jb20vYnAiOiI4MTBkMWQwMC00MzEyLTQzZTUtYmQzMS1kODM3M2ZkZDI0YzciLCJhdXRoX3RpbWUiOjE2MDgxMjQ2MzcsImlzcyI6Imh0dHBzOi8vaWRlbnRpdHkuemFsYW5kby5jb20iLCJleHAiOjE2MDgyMDg3NTAsImlhdCI6MTYwODE5NDM0MH0.FZaFnDUGBD4UufMUZS1XHorHmP8-ZH6HtizAPwC4XvV0r5E0PNqKkWNhuUX_E0zbfM_j5uhGRUxHcbmXVOi8Kg
while [[ true ]]; do
  response=$(curl --write-out '%{http_code}' --silent --output /dev/null -H "Authorization: Bearer $TOK" https://version-test.playground.zalan.do/resources)
  if [ $response != "200" ]; then
    echo "$(date) FUCK: $response"
  fi
done;