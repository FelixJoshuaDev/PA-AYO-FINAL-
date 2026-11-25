/*
 * File:        password-toggle.js
 * Module:      Admin security
 * Purpose:     Show or hide the password in a password field.
 * Responsible: Felix Joshua H. Abing
 */
document.querySelectorAll('.reveal').forEach(button => {
  const input = button.previousElementSibling;
  button.hidden = false;
  button.addEventListener('click', () => {
    const show = input.type === 'password';
    input.type = show ? 'text' : 'password';
    button.setAttribute('aria-pressed', String(show));
  });
});
