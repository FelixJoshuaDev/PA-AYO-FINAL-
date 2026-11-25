/*
 * File:        forms.js
 * Module:      Admin pages
 * Purpose:     Asks before a form with a data-confirm message is sent, and shows which button is working.
 * Responsible: Felix Joshua H. Abing
 */
document.querySelectorAll('form').forEach(form => {
  form.addEventListener('submit', event => {
    if (form.dataset.confirm && !confirm(form.dataset.confirm)) {
      event.preventDefault();
    } else {
      event.submitter?.classList.add('is-busy');
    }
  });
});
// The browser keeps the old page for its Back button; its buttons must work again there.
window.addEventListener('pageshow', () => {
  document.querySelectorAll('.is-busy').forEach(button => button.classList.remove('is-busy'));
});
