import './initComponents.js';

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
const vuetify = new Vuetify(eXo.env.portal.vuetifyPreset);

document.dispatchEvent(new CustomEvent('displayTopBarLoading'));

const appId = 'AgendaTimelineApplication';

//getting language of the PLF
const lang = eXo && eXo.env.portal.language || 'en';

//should expose the locale ressources as REST API 
const url = `${eXo.env.portal.context}/${eXo.env.portal.rest}/i18n/bundle/locale.portlet.Agenda-${lang}.json`;

export function init() {
  exoi18n.loadLanguageAsync(lang, url).then(i18n => {
  // init Vue app when locale ressources are ready
    const eventType = eXo.env.portal.spaceId ? 'allEvents' : 'myEvents';
    new Vue({
      template: `<agenda-timeline-widget id="${appId}" event-type="${eventType}" />`,
      vuetify,
      i18n
    }).$mount(`#${appId}`);
  });
}
