import http from 'k6/http';

export const options = {
  vus: 1,
  iterations: 110,
};

export default function () {
  http.get('http://localhost:8080/1');
}
