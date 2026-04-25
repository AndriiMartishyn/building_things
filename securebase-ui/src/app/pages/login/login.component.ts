import {Component} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {Router} from '@angular/router';
import {AuthService} from '../../services/auth.service';

@Component({
  selector: 'app-login',
  imports: [
    FormsModule
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent {
  email = ''
  password = ''

  constructor(private router: Router, private authService: AuthService) {
  }

  onSubmit() {
    this.authService.login(this.email, this.password).subscribe(
      {
        next: (token) => {
          this.authService.saveToken(token)
          this.router.navigate(['/home'])
        },
        error: (error) => {console.log(error)}
      }
    )
  }
}
