import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';

@Injectable({
  providedIn: 'root'
})

export class DashboardService {

  private dashboardBaseUrl = 'http://localhost:8080/api/v1/dashboard';

  constructor(private http: HttpClient) { }

   getRolesInfoFromDashBoard() {
    const bearerHeader = 'Bearer ' +  localStorage.getItem('jwt');
    return this.http.get(this.dashboardBaseUrl + '/roles', {
     headers: {
       Authorization: bearerHeader
     },
      responseType: "text"
    })
  }
}
