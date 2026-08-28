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

const appId = 'AgendaApplication';

//getting language of the PLF
const lang = eXo && eXo.env.portal.language || 'en';

//should expose the locale ressources as REST API 
const url = `${eXo.env.portal.context}/${eXo.env.portal.rest}/i18n/bundle/locale.portlet.Agenda-${lang}.json`;

export function init() {
  exoi18n.loadLanguageAsync(lang, url).then(i18n => {
    // init Vue app when locale ressources are ready
    const standalone = !!document.getElementById(appId)?.closest('.drawerParent');
    const eventType = !standalone && eXo.env.portal.spaceId ? 'allEvents' : 'myEvents';
    Vue.createApp({
      data() {
        return {
          bodyElementWidth: 0,
        };
      },
      mounted() {
        const el = document.querySelector('#AgendaApplication');
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
        /**
         * Whether the application must render its mobile layout: either the
         * viewport itself is a phone-sized breakpoint, or the container the
         * portlet was dropped into is narrower than the "sm" threshold.
         *
         * The container width comes from the ResizeObserver installed in
         * mounted(), so it is still 0 on the very first render. A width of 0
         * means "not measured yet", NOT "narrower than sm": reading it as
         * narrow made the whole application start in mobile mode on every
         * viewport, and the mobile timeline capped the event query at one
         * page (EXO-89791) — a cap that survived the flip back to desktop.
         * Until a real measurement arrives, only the Vuetify breakpoint
         * decides, which is what the check was before EXO-83963.
         *
         * @returns {Boolean} true when the mobile layout must be rendered
         */
        isMobile() {
          const resposiveMode = this.bodyElementWidth > 0
            && this.bodyElementWidth < this.$vuetify.breakpoint.thresholds.sm;
          return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm' || resposiveMode;
        },
        /**
         * Whether the viewport is tablet-sized, i.e. between the "sm" and
         * "md" Vuetify thresholds.
         *
         * @returns {Boolean} true on a tablet-width viewport
         */
        isTablet() {
          return this.$vuetify.breakpoint.width < this.$vuetify.breakpoint.thresholds.md && this.$vuetify.breakpoint.width >= this.$vuetify.breakpoint.thresholds.sm;
        },
      },
      template: `<agenda id="${appId}" event-type="${eventType}" :standalone="${standalone}" />`,
      vuetify,
      i18n
    }, `#${appId}`, 'Agenda');
  }).finally(() => {
    Vue.prototype.$utils.includeExtensions('VisioConnector');
    Vue.prototype.$utils.includeExtensions('ConnectorsExtensions');
  });

}
