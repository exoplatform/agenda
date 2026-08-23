<template>
  <v-app v-if="displayed">
    <v-card class="application-body" flat>
      <v-list two-line>
        <component
          :is="row.vueComponent"
          v-for="row in rows"
          :key="row.id"
          :settings="settings"
          :connectors="connectors"
          @connectors-loaded="connectors = $event" />
      </v-list>
    </v-card>
    <!--
      The step that creates the calendar receiving the copies. Mounted here
      rather than inside the connector row: connecting from this page used to
      have nowhere to offer it, so the account was created and the destination
      never asked for.
    -->
    <agenda-connector-mirror-calendar-drawer />
  </v-app>
</template>

<script>
/**
 * The rows this page owns, carrying the ranks add-on rows are placed among.
 * They sit in the same sorted list as the contributed ones rather than being
 * fixed markup with an extension slot bolted on: a slot at one position only
 * lets a contributor land there, and the row an add-on needs is rarely last.
 *
 * The gaps are deliberate — an add-on row belongs between the account and the
 * copy switch far more often than after everything.
 */
const OWN_ROWS = [
  {id: 'general', rank: 10, vueComponent: 'agenda-user-general-settings'},
  {id: 'connector', rank: 20, vueComponent: 'agenda-user-connector-settings'},
  {id: 'push', rank: 40, vueComponent: 'agenda-user-push-settings'},
];

export default {
  data: () => ({
    displayed: true,
    connectors: [],
    sections: [],
    settings: {
      agendaDefaultView: 'week',
      agendaWeekStartOn: 'MO',
      showWorkingTime: false,
      workingTimeStart: '08:00',
      workingTimeEnd: '18:00',
    },
  }),
  computed: {
    /**
     * Every row to render, this page's own and the contributed ones, in rank
     * order.
     *
     * @returns {Array} the rows, each carrying the component to render
     */
    rows() {
      return OWN_ROWS.concat(this.sections)
        .sort((a, b) => (a.rank || 0) - (b.rank || 0));
    },
  },
  created() {
    this.$root.$on('agenda-settings-refresh', this.initSettings);
    // Rows contributed by add-ons. They register from modules the page
    // includes AFTER this app is created (includeExtensions runs once the app
    // exists), so a load here alone would race them: each contributor also
    // dispatches this event once registered, and whichever side arrives
    // second finds the other — the same handshake the admin sections use.
    document.addEventListener('agenda-user-sections-refresh', this.refreshSections);
    this.refreshSections();
    this.initSettings();
  },
  mounted() {
    document.addEventListener('hideSettingsApps', (event) => {
      if (event && event.detail && this.id !== event.detail) {
        this.displayed = false;
      }
    });
    document.addEventListener('showSettingsApps', () => this.displayed = true);
    this.$root.$on('agenda-settings-refresh', this.initSettings);
  },
  methods: {
    /**
     * Reloads the add-on-contributed rows from the extension registry. Each
     * entry carries an opaque vueComponent the add-on built from its own
     * globally registered component, so this page never imports add-on code
     * and learns nothing about what the row is for.
     *
     * @returns {void}
     */
    refreshSections() {
      this.sections = (extensionRegistry.loadExtensions('agenda-user-settings', 'sections') || [])
        .filter(section => section.vueComponent);
    },
    initSettings(userSettings) {
      if (userSettings) {
        this.settings = userSettings;
        this.$root.$applicationLoaded();
      } else {
        return this.$settingsService.getUserSettings()
          .then(settings => {
            if (settings) {
              this.settings = settings;
            }
            return this.$nextTick();
          })
          .finally(() => this.$root.$applicationLoaded());
      }
    },
  },
};
</script>
