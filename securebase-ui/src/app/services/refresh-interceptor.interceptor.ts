import {HttpErrorResponse, HttpInterceptorFn, HttpRequest} from '@angular/common/http';
import {inject} from '@angular/core';
import {AuthService} from './auth.service';
import {catchError, finalize, map, Observable, shareReplay, switchMap, tap, throwError} from 'rxjs';
import {Router} from '@angular/router';

/**
 * Refresh-token interceptor.
 *
 * Responsibilities:
 *   1. Stamp the access token onto outbound requests that need auth.
 *   2. On a 401, transparently rotate the token pair and replay the request.
 *   3. Collapse concurrent 401s onto a single /refresh call.
 *   4. Force logout if /refresh itself fails, so we don't loop forever.
 *
 * Token storage model:
 *   - Access token: localStorage, short-lived. Attached as Bearer header.
 *   - Refresh token: httpOnly + SameSite=Strict cookie scoped to /api/v1/refresh.
 *     Sent automatically by the browser; never visible to JavaScript.
 */

/**
 * Module-scoped (tab-scoped) inflight refresh stream.
 *
 * Why share one Observable across all 401s?
 *   If N requests fire in parallel and all return 401, each would call
 *   /refresh independently. Only the first rotation succeeds; the rest
 *   arrive carrying a refresh token that was just revoked, which the
 *   backend treats as token reuse (see UserAuthService:101) and revokes
 *   every token the user has. Sharing one stream guarantees at most one
 *   /refresh per "wave" of 401s.
 */
let refresh$: Observable<string> | null = null;

/** Routes where Bearer must NOT be attached and where a 401 must NOT trigger a refresh. */
const AUTH_ROUTES = ['/login', '/register', '/refresh'];
const isAuthRoute = (url:string) => AUTH_ROUTES.some(route => url.includes(route));

/**
 * Clone a request with an Authorization header.
 * Uses `setHeaders` (replace) rather than `append` so we never end up
 * with multiple Authorization values on the same request.
 */
const withAuth = (req: HttpRequest<unknown>, token: string) =>
  req.clone({setHeaders: {Authorization: `Bearer ${token}`}});

export const refreshInterceptor : HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getToken();
  const router = inject(Router);

  // Outbound: attach the access token to protected requests only.
  //
  // Auth routes are excluded because:
  //   - /login and /register do not need it.
  //   - /refresh MUST NOT receive a (likely expired) access token; it
  //     authenticates from the refresh cookie alone. Sending a stale
  //     Bearer would confuse the backend JwtFilter.
  const outgoingRequest = token && !isAuthRoute(req.url)
    ? withAuth(req, token)
    : req;

  return next(outgoingRequest).pipe(
    catchError((err: HttpErrorResponse) => {
      // 401 is the only signal we treat as "access token is dead".
      // 403 means RBAC failure — refreshing wouldn't help and would
      // hide the real authorization problem from the caller.
      if (err.status !== 401) {
        return throwError(() => (err))
      }

      // A 401 from an auth route (in practice, /refresh) means the
      // refresh token itself is invalid or expired. Calling /refresh
      // again would just produce another 401 → infinite loop.
      // Treat as terminal: clear local state and bounce to /login.
      if (isAuthRoute(req.url)) {
        authService.logout();
        router.navigate(['/login']).then(() => {});
        return throwError(() => err);
      }

      // Lazily start (or reuse) the shared inflight refresh.
      //   - map:        we only care about the access token.
      //   - tap:        persist it so subsequent requests pick it up.
      //   - shareReplay(1): late subscribers (other 401s in the same
      //                 wave) get the same value without re-firing.
      //   - finalize:   clear the slot so the NEXT wave of 401s can
      //                 start a fresh refresh.
      refresh$ ??= authService.refreshToken().pipe(
        tap(t => authService.saveToken(t)),
        shareReplay(1),
        finalize(() => {
          refresh$ = null
        }));

      // When the refresh resolves, replay the original request with
      // the freshly issued access token. If the refresh itself errors,
      // treat it as terminal — clear local state and bounce to login.
      return refresh$.pipe(
        switchMap(newToken => next(withAuth(req, newToken))),
        catchError(refreshErr => {
          authService.logout();
          router.navigate(['/login']);
          return throwError(() => refreshErr);
        }),
      );
    }),
  );
};
