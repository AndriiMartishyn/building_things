import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService} from '../../services/auth.service';
import {jwtDecode} from 'jwt-decode';

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrl: './home.component.css'
})
export class HomeComponent {

  email = ''

  constructor(private authService: AuthService, private router: Router) {
    var token = authService.getToken();
    if (token != null) {
      const claims = jwtDecode<{sub : string}>(token);
      if (claims.sub != null) {
        this.email = claims.sub
      }
    }
  }

  logout() {
    this.authService.logout()
    this.router.navigate(['/login']);
  }
}
