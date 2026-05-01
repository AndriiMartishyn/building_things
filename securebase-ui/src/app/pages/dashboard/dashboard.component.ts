import {Component, OnInit} from '@angular/core';
import {jwtDecode} from 'jwt-decode';
import {AuthService} from '../../services/auth.service';
import {Router} from '@angular/router';
import {NgIf} from '@angular/common';

interface JwtClaims {
  sub: string;   // email
  iat: number;   // issued at (unix seconds)
  exp: number;   // expiry (unix seconds)
  iss: string;   // issuer
}

@Component({
  selector: 'app-home',
  templateUrl: './dashboard.component.html',
  imports: [
    NgIf
  ],
  styleUrl: './dashboard.component.css'
})

export class DashboardComponent implements OnInit {
  email = '';
  issuer = '';
  issuedAt = '';
  timeLeft = '';
  expired = false;

  private expSeconds = 0;
  private timer: any;


  constructor(private authService: AuthService, private router: Router) {

  }

  ngOnInit() {
    const token = this.authService.getToken();
    if (!token) {
      this.router.navigate(['/login']);
      return;
    }
    const tokenClaims = jwtDecode<JwtClaims>(token);
    this.email = tokenClaims.sub;
    this.issuer = tokenClaims.iss;
    this.issuedAt = new Date(tokenClaims.iat * 1000).toLocaleTimeString();
    this.expSeconds = tokenClaims.exp;
    this.updateCountdown();
    this.timer = setInterval(() => {this.updateCountdown();}, 1000);

  }

  private updateCountdown() {
    const remaining = this.expSeconds  - Math.floor(Date.now() / 1000);
    if (remaining <= 0) {
      this.expired = true;
      this.timeLeft = '0:00';
      return;
    }
    const m = Math.floor(remaining / 60);
    const s = remaining % 60;
    this.timeLeft = `${m}:${s.toString().padStart(2, '0')}`;

  }

  ngOnDestroy() {
    clearInterval(this.timer);
  }

  protected logout(){
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
