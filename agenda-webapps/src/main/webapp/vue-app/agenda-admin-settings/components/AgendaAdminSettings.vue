<template>
  <v-app class="connectorsAdminSettings">
    <v-main class="application-body pa-5">
      <component
        :is="section.vueComponent"
        v-for="section in sections"
        :key="section.id"
        :settings="settings" />
    </v-main>
  </v-app>
</template>

<script>
/*
 * The page's own sections, declared exactly like a contributed one so that
 * ONE list decides the order of everything on the page.
 *
 * They used to be written straight into the template, above the extension
 * loop, which made an add-on's rank sort it against nothing: whatever it
 * asked for, it landed after both of these. The CalDAV registry has to come
 * before the connectors table it feeds (EXO-89757), and there is no rank that
 * expresses that while these two are hardcoded.
 *
 * The embedded-map settings stay first by product decision: the page opens on
 * a setting every deployment has, rather than on a registry that is empty
 * until someone declares a server.
 *
 * Ranks are spaced by ten so a section can be slipped between two of them
 * without renumbering. `vueComponent` holds a NAME here and a component
 * OBJECT for a contributed section; `<component :is>` takes either, and the
 * name resolves because initComponents.js registers both globally.
 */
const BUILT_IN_SECTIONS = [
  {id: 'agendaEmbedMap', rank: 10, vueComponent: 'agenda-admin-embed-map-settings'},
  {id: 'agendaConnectors', rank: 30, vueComponent: 'agenda-admin-connector-settings'},
];

export default {
  data: () => ({
    settings: null,
    sections: [],
  }),
  created() {
    // Sections contributed by add-ons. They register from modules the page
    // includes AFTER this app is created (includeExtensions runs once the app
    // exists), so a load here alone would race them: each contributor also
    // dispatches this event once registered, and whichever side arrives
    // second finds the other — the same handshake the connectors table uses
    // with agenda-connectors-refresh.
    document.addEventListener('agenda-admin-sections-refresh', this.refreshSections);
    this.refreshSections();
    this.refreshSettings()
      .finally(() => this.$root.$applicationLoaded());
  },
  methods: {
    refreshSettings() {
      return this.$settingsService.getUserSettings()
        .then(settings => this.settings = settings);
    },
    /**
     * Rebuilds the page: this app's own sections merged with the ones add-ons
     * contributed, ordered by rank in a single sort.
     *
     * The built-ins are listed first in the array only so that a contributed
     * section carrying an equal rank renders after them — Array.sort is stable
     * in every engine the portal runs on. Each contributed entry carries an
     * opaque vueComponent the add-on built from its own globally registered
     * component, so this page never imports add-on code, and an add-on that
     * registers nothing simply leaves the built-ins alone.
     *
     * @returns {void}
     */
    refreshSections() {
      const contributed = (extensionRegistry.loadExtensions('agenda-admin-settings', 'sections') || [])
        .filter(section => section.vueComponent);
      this.sections = BUILT_IN_SECTIONS.concat(contributed)
        .sort((a, b) => (a.rank || 0) - (b.rank || 0));
    },
  }
};
</script>
