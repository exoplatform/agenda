/*
 * Copyright (C) 2025 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

const appId = 'agendaEvent-content-link';

export function init() {
  document.addEventListener('content-link-agendaEvent-drawer', openAgendaEventDrawer);
}

function openAgendaEventDrawer(event) {
  window.require(['SHARED/eXoVueI18n', 'PORTLET/agenda/Agenda'], exoi18n => initAgenda(exoi18n, event.detail));
}

async function initAgenda(exoi18n, eventId) {
  if (!document.querySelector(`#${appId}`)) {
    const parent = document.createElement('div');
    parent.id = appId;
    document.querySelector('#vuetify-apps').appendChild(parent);
    await Promise.all([
      initAgendaApp(exoi18n),
      Vue.prototype.$utils.importSkin('agenda', 'Agenda'),
    ]);
  }
  document.dispatchEvent(new CustomEvent('content-link-agendaEvent-drawer-open', {detail: eventId}));
}

export function initAgendaApp(exoi18n) {
  const lang = eXo.env.portal.language;
  const url = `/agenda/i18n/locale.portlet.Agenda?lang=${lang}`;
  return new Promise(resolve => exoi18n.loadLanguageAsync(lang, url).then(i18n => {
    Vue.createApp({
      template: `<agenda id="${appId}" event-type="allEvents" hide-app />`,
      created() {
        document.addEventListener('content-link-agendaEvent-drawer-open', this.openAgendaEvent);
      },
      mounted() {
        resolve();
      },
      beforeDestroy() {
        document.removeEventListener('content-link-agendaEvent-drawer-open', this.openAgendaEvent);
      },
      methods: {
        openAgendaEvent(event) {
          this.$root.$emit('agenda-event-details-by-id', event.detail);
        }
      },
      vuetify: Vue.prototype.vuetifyOptions,
      i18n
    }, `#${appId}`, 'Agenda Content Link');
  }).finally(() => {
    Vue.prototype.$utils.includeExtensions('VisioConnector');
    Vue.prototype.$utils.includeExtensions('ConnectorsExtensions');
  }));
}
