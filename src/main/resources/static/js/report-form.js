/*
 * File:        report-form.js
 * Module:      Report submission form (S2)
 * Purpose:     Photo drag and drop, preview and early type/size check. The server repeats every check.
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
