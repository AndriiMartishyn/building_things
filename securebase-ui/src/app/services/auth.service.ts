import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';

@Injectable({
  providedIn: 'root'
})
export class AuthService {

  private baseUrl = 'http://localhost:8080/api/v1';

  constructor(private http: HttpClient) {
  }

  register(email: string, password: string) {
    return this.http.post(`${this.baseUrl}/register`, {email, password});
  }

  login(email: string, password: string) {
    return this.http.post(`${this.baseUrl}/login`,
      {email, password},
      {responseType: 'text', withCredentials: true})
  }

  refreshToken() {
    return this.http.post< { accessToken: string }>(`${this.baseUrl}/refresh`, {}, {withCredentials: true});
  }

  saveToken(token: string) {
    localStorage.setItem('jwt', token);
  }

  getToken() {
    return localStorage.getItem('jwt');
  }

  isLoggedIn() {
    return this.getToken() !== null;
  }

  logout() {
    localStorage.removeItem('jwt');
  }
}
