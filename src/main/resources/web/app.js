'use strict';

let garageState = null;
let selectedSpot = null;
let currentLookup = null;
let toastTimer;

const $ = id => document.getElementById(id);
const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
const money = amount => new Intl.NumberFormat('en-IN', {style:'currency', currency:'INR', minimumFractionDigits:2}).format(Number(amount));
const when = time => time ? new Intl.DateTimeFormat('en-IN', {day:'2-digit',month:'short',year:'numeric',hour:'2-digit',minute:'2-digit'}).format(new Date(time)) : '—';
const shortTicket = ticket => ticket.length > 18 ? `${ticket.slice(0, 11)}…${ticket.slice(-5)}` : ticket;

async function api(path, method = 'GET', body) {
  const response = await fetch(path, {method, headers: body ? {'Content-Type':'application/json'} : {}, body: body ? JSON.stringify(body) : undefined});
  let data;
  try { data = await response.json(); } catch (_) { throw new Error('The server did not return a valid response.'); }
  if (!response.ok) {
    const error = new Error(data.message || 'The request could not be completed.');
    error.code = data.error;
    error.updatedFee = data.updatedFee;
    throw error;
  }
  return data;
}

function showToast(message, isError = false) {
  const toast = $('toast');
  toast.textContent = message;
  toast.className = `toast show${isError ? ' error' : ''}`;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.className = 'toast', 4300);
}

function setState(state) {
  garageState = state;
  const chosen = state.spots.find(spot => spot.code === selectedSpot);
  if (chosen && (chosen.status !== 'AVAILABLE' || !chosen.compatibleTypes.includes($('vehicleType').value))) selectedSpot = null;
  render();
}

async function refresh(silent = false) {
  try {
    setState(await api('/api/state'));
    if (!silent) showToast('Garage status is up to date.');
  } catch (error) {
    if (!silent || !garageState) showToast(`Cannot reach the Java server: ${error.message}`, true);
  }
}

function render() {
  if (!garageState) return;
  const {overview: o, spots, active, history} = garageState;
  $('garageTitle').textContent = garageState.garageName;
  $('heroPrice').textContent = money(o.fee);
  $('heroTier').textContent = o.tier.replace(' - ', ' · ');
  $('metricTotal').textContent = o.capacity;
  $('metricAvailable').textContent = o.available;
  $('metricOccupied').textContent = o.occupied;
  $('metricUnavailable').textContent = o.reserved + o.outOfService;
  $('bookingQuote').textContent = money(o.fee);
  $('floorRatio').textContent = `${o.occupied} / ${o.capacity} occupied`;
  const percent = Math.round(o.occupied / o.capacity * 100);
  $('occupancyPercent').textContent = `${percent}%`;
  $('occupancyBar').style.width = `${percent}%`;
  $('activeCount').textContent = `${active.length} active`;
  $('sideActive').textContent = active.length;
  $('bookButton').disabled = o.available === 0;
  $('bookButton').innerHTML = o.available === 0 ? 'Garage currently full' : 'Confirm parking <svg><use href="#i-arrow"></use></svg>';
  renderSpots(spots);
  renderSessions(active);
  renderHistory(history);
  renderSelection();
  if (currentLookup) {
    const updated = active.find(item => item.ticketCode === currentLookup.ticketCode);
    if (updated) currentLookup = updated;
    else { currentLookup = null; $('lookupResult').innerHTML = '<strong>Ticket has been completed.</strong><p>This spot is now available again.</p>'; }
  }
}

function renderSpots(spots) {
  const type = $('vehicleType').value;
  $('parkingGrid').innerHTML = spots.map(spot => {
    const compatible = spot.compatibleTypes.includes(type);
    const disabled = spot.status !== 'AVAILABLE' || !compatible;
    const className = selectedSpot === spot.code ? 'selected' : spot.status.toLowerCase() + (compatible ? '' : ' incompatible');
    const label = spot.status === 'AVAILABLE' && !compatible ? 'Wrong type' : ({AVAILABLE:'Available',OCCUPIED:'Occupied',RESERVED:'Reserved',OUT_OF_SERVICE:'Closed'}[spot.status] || spot.status);
    return `<button class="spot ${className}" type="button" data-code="${escapeHtml(spot.code)}" ${disabled ? 'disabled' : ''} aria-label="${escapeHtml(spot.code)} ${escapeHtml(spot.type.toLowerCase())} bay, ${escapeHtml(label)}" aria-pressed="${selectedSpot === spot.code}">
      <span>${escapeHtml(spot.code)}</span><small>${spot.type === 'COMPACT' ? 'MOTORCYCLE' : 'CAR / BIKE'}</small><span class="spot-status">${escapeHtml(label)}</span></button>`;
  }).join('');
}

function renderSelection() {
  const spot = garageState.spots.find(item => item.code === selectedSpot);
  $('selectedSpotText').textContent = spot ? spot.code : 'No spot selected';
  $('selectedSpotSub').textContent = spot ? `${spot.type === 'COMPACT' ? 'Motorcycle' : 'Standard'} bay · Ground floor` : 'Choose a green spot on the map';
}

function renderSessions(sessions) {
  $('activeBody').innerHTML = sessions.length ? sessions.map(item => `<tr>
    <td><span class="ticket-code" title="${escapeHtml(item.ticketCode)}">${escapeHtml(shortTicket(item.ticketCode))}</span></td>
    <td><strong>${escapeHtml(item.registration)}</strong><br>${item.vehicleType === 'CAR' ? 'Car' : 'Motorcycle'}</td>
    <td><strong>${escapeHtml(item.spotCode)}</strong></td><td>${escapeHtml(when(item.entryTime))}</td>
    <td><strong>${money(item.lockedFee)}</strong></td><td><span class="status-pill">ACTIVE</span></td>
    <td><button class="row-button" data-exit="${escapeHtml(item.ticketCode)}" type="button">Process exit</button></td></tr>`).join('') : '<tr><td class="empty-row" colspan="7">No active sessions yet. Select a spot above to park your first vehicle.</td></tr>';
}

function renderHistory(history) {
  const needle = $('historyFilter').value.trim().toLowerCase();
  const filtered = history.filter(item => !needle || [item.ticketCode,item.registration,item.spotCode,item.entryTime,item.exitTime,item.status,item.paymentStatus].some(value => String(value).toLowerCase().includes(needle)));
  $('historyBody').innerHTML = filtered.length ? filtered.slice().reverse().map(item => `<tr>
    <td><span class="ticket-code" title="${escapeHtml(item.ticketCode)}">${escapeHtml(shortTicket(item.ticketCode))}</span></td><td><strong>${escapeHtml(item.registration)}</strong></td>
    <td>${escapeHtml(item.spotCode)}</td><td>${escapeHtml(when(item.entryTime))}</td><td>${escapeHtml(when(item.exitTime))}</td>
    <td><strong>${money(item.lockedFee)}</strong></td><td><span class="status-pill paid">${escapeHtml(item.paymentStatus)}</span></td></tr>`).join('') : `<tr><td class="empty-row" colspan="7">${history.length ? 'No history records match your search.' : 'No completed sessions yet. History appears after an exit.'}</td></tr>`;
}

function askConfirmation({eyebrow, title, message, accept = 'Confirm'}) {
  const dialog = $('confirmDialog');
  $('dialogEyebrow').textContent = eyebrow;
  $('dialogTitle').textContent = title;
  $('dialogMessage').textContent = message;
  $('dialogAccept').textContent = accept;
  return new Promise(resolve => {
    function finish(answer) { dialog.close(); resolve(answer); }
    $('dialogAccept').onclick = () => finish(true);
    $('dialogCancel').onclick = () => finish(false);
    dialog.oncancel = event => { event.preventDefault(); finish(false); };
    dialog.showModal();
  });
}

function showReceipt(item, isExit = false) {
  $('ticketEyebrow').textContent = isExit ? 'EXIT COMPLETE' : 'PARKING CONFIRMED';
  $('ticketTitle').textContent = isExit ? 'All checked out.' : "You're all set.";
  $('ticketDescription').textContent = isExit ? 'The spot has been released and this session is in history.' : 'Keep this ticket code for a smooth exit.';
  const rows = [
    ['Ticket ID', item.ticketCode], ['Vehicle', item.registration + ' · ' + (item.vehicleType === 'CAR' ? 'Car' : 'Motorcycle')],
    ['Parking space', item.spotCode], ['Entry time', when(item.entryTime)],
    ...(isExit ? [['Exit time', when(item.exitTime)], ['Payment', 'Simulated · PAID']] : []),
    ['Fixed session fee', money(item.lockedFee)]
  ];
  $('ticketDetails').innerHTML = rows.map(([name,value],index) => `<div class="receipt-row ${index === rows.length - 1 ? 'emphasis' : ''}"><span>${escapeHtml(name)}</span><strong>${escapeHtml(value)}</strong></div>`).join('');
  $('ticketDialog').showModal();
}

function registrationIsValid(value) {
  const normalized = value.replace(/[\s-]/g, '').toUpperCase();
  return /^(?=.*[A-Z])(?=.*[0-9])[A-Z0-9]{5,15}$/.test(normalized);
}

async function submitEntry(event) {
  event.preventDefault();
  if (!garageState) return;
  const registration = $('registration').value.trim();
  if (!registrationIsValid(registration)) return showToast('Enter a valid registration with 5–15 letters and numbers.', true);
  if (!selectedSpot) return showToast('Choose an available, compatible spot first.', true);
  const type = $('vehicleType').value;
  let fee = garageState.overview.fee;
  // A changed quote always requires a NEW explicit approval. Never silently retry at a new fee.
  for (let attempt = 0; attempt < 4; attempt++) {
    const approved = await askConfirmation({eyebrow:'REVIEW YOUR PARKING', title:'Confirm your entry',
      message:`${registration.toUpperCase()} will park in ${selectedSpot}. Your one-time, locked fee is ${money(fee)}. Proceed?`, accept:`Confirm · ${money(fee)}`});
    if (!approved) return;
    const bookButton = $('bookButton');
    bookButton.disabled = true;
    try {
      const result = await api('/api/entry', 'POST', {registration, vehicleType:type, spotCode:selectedSpot, confirmedFee:String(fee)});
      selectedSpot = null;
      $('registration').value = '';
      setState(result.state);
      showReceipt(result.session);
      showToast(`${result.session.spotCode} booked. Your fee is locked at ${money(result.session.lockedFee)}.`);
      return;
    } catch (error) {
      if (error.code === 'QUOTE_CHANGED') {
        await refresh(true);
        if (!selectedSpot) return showToast('The selected spot is no longer available. Choose another.', true);
        fee = error.updatedFee;
        showToast(`The current rate changed to ${money(fee)}. Please review it again.`);
        continue;
      }
      await refresh(true);
      showToast(error.message, true);
      return;
    } finally { bookButton.disabled = false; }
  }
  showToast('The price changed repeatedly. Refresh and try again.', true);
}

function renderLookup(session) {
  $('lookupResult').className = 'lookup-result';
  $('lookupResult').innerHTML = `<div class="lookup-result-head"><strong>${escapeHtml(session.registration)}</strong><span class="status-pill">ACTIVE</span></div>
    <div class="lookup-details"><div><span>Ticket ID</span><strong title="${escapeHtml(session.ticketCode)}">${escapeHtml(shortTicket(session.ticketCode))}</strong></div><div><span>Parking space</span><strong>${escapeHtml(session.spotCode)}</strong></div>
    <div><span>Entry time</span><strong>${escapeHtml(when(session.entryTime))}</strong></div><div><span>Agreed fixed fee</span><strong>${money(session.lockedFee)}</strong></div></div>
    <button class="btn btn-dark btn-full" type="button" id="exitFound">Confirm exit · ${money(session.lockedFee)}</button>`;
  $('exitFound').addEventListener('click', processExit);
}

async function lookupTicket(event) {
  if (event) event.preventDefault();
  const lookup = $('lookupInput').value.trim();
  if (!lookup) return showToast('Enter a ticket ID or registration.', true);
  try {
    const result = await api('/api/lookup', 'POST', {lookup});
    currentLookup = result.session;
    renderLookup(currentLookup);
    $('exit').scrollIntoView({behavior:'smooth', block:'center'});
  } catch (error) { currentLookup = null; showToast(error.message, true); }
}

async function processExit() {
  if (!currentLookup) return;
  const session = currentLookup;
  const approved = await askConfirmation({eyebrow:'CONFIRM CHECKOUT',title:'Release this space?',
    message:`Exit ${session.registration} from ${session.spotCode}? The original fixed fee is ${money(session.lockedFee)}. Payment is simulated for this project.`,accept:'Complete exit'});
  if (!approved) return;
  try {
    const result = await api('/api/exit', 'POST', {lookup:session.ticketCode});
    currentLookup = null;
    $('lookupInput').value = '';
    $('lookupResult').className = 'lookup-placeholder';
    $('lookupResult').innerHTML = '<span class="placeholder-icon"><svg><use href="#i-check"></use></svg></span><strong>Space released</strong><p>The current quote for new arrivals has been updated.</p>';
    setState(result.state);
    showReceipt(result.session, true);
    showToast(`${result.session.spotCode} is available again. Fee paid: ${money(result.session.lockedFee)}.`);
  } catch (error) { await refresh(true); showToast(error.message, true); }
}

document.addEventListener('DOMContentLoaded', () => {
  $('today').textContent = new Intl.DateTimeFormat('en-IN', {weekday:'short',day:'numeric',month:'short',year:'numeric'}).format(new Date());
  $('bookingForm').addEventListener('submit', submitEntry);
  $('lookupForm').addEventListener('submit', lookupTicket);
  $('historyFilter').addEventListener('input', () => garageState && renderHistory(garageState.history));
  $('refreshButton').addEventListener('click', () => refresh());
  $('ticketClose').addEventListener('click', () => $('ticketDialog').close());
  $('registration').addEventListener('input', event => event.target.value = event.target.value.toUpperCase());
  $('vehicleType').addEventListener('change', () => {
    if (!garageState) return;
    const spot = garageState.spots.find(item => item.code === selectedSpot);
    if (spot && !spot.compatibleTypes.includes($('vehicleType').value)) selectedSpot = null;
    renderSpots(garageState.spots);
    renderSelection();
  });
  $('parkingGrid').addEventListener('click', event => {
    const button = event.target.closest('button[data-code]');
    if (!button || button.disabled || !garageState) return;
    selectedSpot = selectedSpot === button.dataset.code ? null : button.dataset.code;
    renderSpots(garageState.spots);
    renderSelection();
  });
  $('activeBody').addEventListener('click', event => {
    const button = event.target.closest('button[data-exit]');
    if (!button || !garageState) return;
    const session = garageState.active.find(item => item.ticketCode === button.dataset.exit);
    if (!session) return;
    currentLookup = session;
    $('lookupInput').value = session.ticketCode;
    renderLookup(session);
    $('exit').scrollIntoView({behavior:'smooth',block:'center'});
  });
  document.querySelectorAll('.nav-link').forEach(link => link.addEventListener('click', () => {
    document.querySelectorAll('.nav-link').forEach(item => item.classList.remove('current'));
    link.classList.add('current');
  }));
  refresh(true);
  setInterval(() => { if (!document.hidden && !$('confirmDialog').open) refresh(true); }, 7000);
});
