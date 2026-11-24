/*
 * File:        report-form.js
 * Module:      Report submission form (S2)
 * Purpose:     Photo drag and drop, preview, early checks and double-submit guard. The server repeats every check.
 * Responsible: Reuel Matthew Arcilla
 * Created:     2026-11-24
 */
(() => {
  const MAX_BYTES = 5 * 1024 * 1024;
  const input = document.getElementById('photoFile');
  const zone = document.getElementById('dropzone');
  const preview = document.getElementById('photo-preview');
  const hint = document.getElementById('photo-hint');
  const error = document.getElementById('photo-error');
  const defaultHint = hint.textContent;
  const submit = input.form.querySelector('button[type="submit"]');
  const submitLabel = submit.textContent;

  function showSelection() {
    const file = input.files[0];
    error.textContent = '';
    if (file && !input.accept.split(',').includes(file.type)) {
      error.textContent = 'The photo must be a JPEG, PNG or WebP image.';
    } else if (file && file.size > MAX_BYTES) {
      error.textContent = 'The photo must be 5 MB or smaller.';
    }
    if (error.textContent) {
      input.value = '';
    }
    const accepted = input.files[0];
    URL.revokeObjectURL(preview.src);
    preview.hidden = !accepted;
    preview.src = accepted ? URL.createObjectURL(accepted) : '';
    hint.textContent = accepted ? accepted.name : defaultHint;
  }

  // A photo can take several seconds to upload; a second click would create a duplicate report.
  function setSubmitting(active) {
    submit.disabled = active;
    submit.textContent = active ? 'Submitting...' : submitLabel;
  }

  input.form.addEventListener('submit', () => setSubmitting(true));
  window.addEventListener('pageshow', () => setSubmitting(false));
  input.addEventListener('change', showSelection);
  ['dragover', 'dragleave', 'drop'].forEach(type => zone.addEventListener(type, event => {
    event.preventDefault();
    zone.classList.toggle('dragging', type === 'dragover');
  }));
  zone.addEventListener('drop', event => {
    if (event.dataTransfer.files.length) {
      input.files = event.dataTransfer.files;
      showSelection();
    }
  });
})();
