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
  // When the timeline is rendered standalone (e.g. pinned in the App Center
  // drawer through the portlet-viewer) it is not bound to the visited space,
  // so it must keep its own configuration instead of adopting the current
  // space context. Such a render lives inside a navigation drawer, unlike a
  // portlet embedded in a space page (which sits in #UISiteBody).
  const standalone = !!document.getElementById(appId)?.closest('.v-navigation-drawer');
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
          headerTitle,
          standalone
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
