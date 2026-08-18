<template>
  <v-app class="connectorsAdminSettings">
    <v-main class="application-body pa-5">
      <agenda-admin-embed-map-settings :settings="settings" />
      <agenda-admin-connector-settings :settings="settings" />
      <component
        :is="section.vueComponent"
        v-for="section in sections"
        :key="section.id"
        :settings="settings" />
    </v-main>
  </v-app>
</template>

<script>
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
     * Reloads the add-on-contributed sections from the extension registry,
     * ordered by rank. Each entry carries an opaque vueComponent the add-on
     * built from its own globally registered component, so this page never
     * imports add-on code.
     *
     * @returns {void}
     */
    refreshSections() {
      this.sections = (extensionRegistry.loadExtensions('agenda-admin-settings', 'sections') || [])
        .filter(section => section.vueComponent)
        .sort((a, b) => (a.rank || 0) - (b.rank || 0));
    },
  }
};
</script>
