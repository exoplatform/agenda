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

//getting language of the PLF
const lang = eXo && eXo.env.portal.language || 'en';

//should expose the locale ressources as REST API 
const url = `${eXo.env.portal.context}/${eXo.env.portal.rest}/i18n/bundle/locale.portlet.Agenda-${lang}.json`;

export function init(appId, canEdit, settings, settingsSaveUrl, settingName, headerTitle) {
  exoi18n.loadLanguageAsync(lang, url).then(i18n => {
  // init Vue app when locale ressources are ready
    const eventType = eXo.env.portal.spaceId ? 'allEvents' : 'myEvents';
    Vue.createApp({
      data() {
        return {
          bodyElementWidth: 0,
          hover: false,
          timelineSettings: settings,
          settingsSaveUrl,
          canEdit,
          settingName,
          headerTitle
        };
      },
      mounted() {
        const el = document.querySelector(`#${appId}`);
        this.resizeObserver = new ResizeObserver((entries) => {
          for (const entry of entries) {
            const { width } = entry.contentRect;
            this.bodyElementWidth = width;
          }
        });
        this.resizeObserver.observe(el);
      },
      beforeUnmount() {
        if (this.resizeObserver) {
          this.resizeObserver.disconnect();
        }
      },
      computed: {
        isMobile() {
          return this.$vuetify.breakpoint.smAndDown;
        },
        isTimelineView() {
          return this.isMobile ? true : this.bodyElementWidth < this.$vuetify.breakpoint.thresholds.sm;
        },
      },
      template: `<agenda-timeline-widget id="${appId}" event-type="${eventType}" />`,
      vuetify,
      i18n
    }, `#${appId}`, 'Agenda Timeline');
  }).finally(() => {
    Vue.prototype.$utils.includeExtensions('VisioConnector');
    Vue.prototype.$utils.includeExtensions('ConnectorsExtensions');
  });
}
