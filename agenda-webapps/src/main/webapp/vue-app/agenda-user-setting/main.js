import './initComponents.js';

import * as eventService from '../agenda/js/EventService.js';
import * as calendarService from '../agenda/js/CalendarService.js';
import * as agendaUtils from '../agenda/js/AgendaUtils.js';

const userTimeZone = agendaUtils.getUserTimezone();

// get overrided components if exists
if (extensionRegistry) {
  const components = extensionRegistry.loadComponents('AgendaSettings');
  if (components && components.length > 0) {
    components.forEach(cmp => {
      Vue.component(cmp.componentName, cmp.componentOptions);
    });
  }
}

Vue.use(Vuetify);
const vuetify = new Vuetify({
  dark: true,
  iconfont: '',
});

document.dispatchEvent(new CustomEvent('displayTopBarLoading'));

if (!Vue.prototype.$calendarService) {
  window.Object.defineProperty(Vue.prototype, '$calendarService', {
    value: calendarService,
  });
}
if (!Vue.prototype.$eventService) {
  window.Object.defineProperty(Vue.prototype, '$eventService', {
    value: eventService,
  });
}
if (!Vue.prototype.$agendaUtils) {
  window.Object.defineProperty(Vue.prototype, '$agendaUtils', {
    value: agendaUtils,
  });
}
if (!Vue.prototype.$userTimeZone) {
  window.Object.defineProperty(Vue.prototype, '$userTimeZone', {
    value: userTimeZone,
  });
}

const appId = 'AgendaSettingsApplication';

//getting language of the PLF
const lang = eXo && eXo.env.portal.language || 'en';

//should expose the locale ressources as REST API 
const url = `${eXo.env.portal.context}/${eXo.env.portal.rest}/i18n/bundle/locale.portlet.Agenda-${lang}.json`;

export function init() {
  exoi18n.loadLanguageAsync(lang, url).then(i18n => {
    // init Vue app when locale ressources are ready
    new Vue({
      template: `<agenda-user-settings id="${appId}" />`,
      vuetify,
      i18n
    }).$mount(`#${appId}`);
  });
}
