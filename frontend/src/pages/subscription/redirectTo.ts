/** Tam sayfa yönlendirme (ödeme sağlayıcısının sayfasına). Testte taklit edilebilsin diye ayrı modül. */
export function redirectTo(url: string): void {
  window.location.href = url;
}
