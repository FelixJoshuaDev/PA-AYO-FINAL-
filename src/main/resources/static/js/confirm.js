/*
 * File:        confirm.js
 * Module:      Admin pages
 * Purpose:     Asks before sending a form that has a data-confirm message, such as deleting a report.
 * Responsible: Felix Joshua H. Abing
 * Created:     2026-11-25
 */
document.querySelectorAll('form[data-confirm]').forEach(form => {
  form.addEventListener('submit', event => {
    if (!confirm(form.dataset.confirm)) {
      event.preventDefault();
    }
  });
});
