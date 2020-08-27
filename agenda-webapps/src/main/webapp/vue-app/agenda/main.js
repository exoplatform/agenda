import './initComponents.js';

import * as eventService from './js/EventService.js';
import * as calendarService from './js/CalendarService.js';
import * as agendaUtils from './js/AgendaUtils.js';

const userTimeZone = agendaUtils.getUserTimezone();

// get overrided components if exists
if (extensionRegistry) {
  const components = extensionRegistry.loadComponents('Agenda');
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

window.Object.defineProperty(Vue.prototype, '$calendarService', {
  value: calendarService,
});
window.Object.defineProperty(Vue.prototype, '$eventService', {
  value: eventService,
});
window.Object.defineProperty(Vue.prototype, '$agendaUtils', {
  value: agendaUtils,
});
window.Object.defineProperty(Vue.prototype, '$userTimeZone', {
  value: userTimeZone,
});

document.dispatchEvent(new CustomEvent('displayTopBarLoading'));

const appId = 'AgendaApplication';

//getting language of the PLF
const lang = eXo && eXo.env.portal.language || 'en';

//should expose the locale ressources as REST API 
const url = `${eXo.env.portal.context}/${eXo.env.portal.rest}/i18n/bundle/locale.portlet.Agenda-${lang}.json`;

export function init() {
  exoi18n.loadLanguageAsync(lang, url).then(i18n => {
    // init Vue app when locale ressources are ready
    new Vue({
      template: `<agenda id="${appId}" calendar-type="week" />`,
      vuetify,
      i18n
    }).$mount(`#${appId}`);
  });
}
