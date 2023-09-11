function loadPersonas() {
  let personas = localStorage.getItem('personas');
  if (personas) {
    return JSON.parse(personas);
  }
}

function removePersona(id) {
  let personas = loadPersonas();
  localStorage.setItem('personas', JSON.stringify(personas.filter(function(persona) { persona.id != id; })));
}

function addPersona(persona) {
  let personas = loadPersonas() || [];
  personas.push(persona);
  localStorage.setItem('personas', JSON.stringify(personas));
}

function submitPersona(persona) {
  document.getElementById('input-username').value = persona.userName;
  document.getElementById('input-name').value = persona.displayName;
  document.getElementById('input-email').value = persona.email;
  document.querySelector('form').submit();
}

function renderPersona(persona) {
  let el = document.createElement('div');
  el.classList.add('rounded-md', 'border', 'border-slate-700', 'hover:bg-slate-800', 'px-4', 'py-3', 'text-white', 'text-xs', 'font-sans', 'cursor-pointer', 'relative', 'group');

  let userNameEl = document.createElement('div');
  userNameEl.innerText = persona.userName;
  userNameEl.classList.add('text-sm', 'font-bold');

  let displayNameEl = document.createElement('div');
  displayNameEl.classList.add('mt-1');
  displayNameEl.innerText = persona.displayName + ' / ' + persona.email;

  let removeEl = document.createElement('div');
  removeEl.classList.add('text-[10px]', 'text-red-300', 'hover:underline', 'cursor-pointer', 'absolute', 'right-2', 'top-1', 'opacity-0', 'group-hover:opacity-100', 'transition-all');
  removeEl.innerText = 'Remove';

  el.appendChild(userNameEl);
  el.appendChild(displayNameEl);
  el.appendChild(removeEl);

  el.addEventListener('click', function() {
    submitPersona(persona);
  });
  removeEl.addEventListener('click', function(event) {
    event.stopPropagation();
    removePersona(persona.id);
    el.remove();
  });

  return el;
}

function renderPersonas(personas) {
  let listEl = document.getElementById('personas');
  listEl.innerHTML = '';
  personas.forEach(function(persona) {
    listEl.appendChild(renderPersona(persona));
  });
}

addEventListener('DOMContentLoaded', function() {
  if (!document.getElementById('personas')) { return; }

  let personas = loadPersonas();
  if (personas) {
    renderPersonas(personas);
  }
  document.getElementById('submit-persona').addEventListener('click', function(event) {
    event.preventDefault();
    addPersona({
      userName: document.getElementById('input-username').value,
      displayName: document.getElementById('input-name').value,
      email: document.getElementById('input-email').value,
      id: crypto.randomUUID()
    })
    event.target.closest('form').submit();
  });

})