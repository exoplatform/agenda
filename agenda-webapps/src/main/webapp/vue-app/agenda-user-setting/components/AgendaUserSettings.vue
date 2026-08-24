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
          :nested-sections="row.id === 'connector' && nestedCalendarSections || []"
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
  {id: 'remote-connectors', rank: 35, vueComponent: 'agenda-user-remote-connectors-settings'},
  {id: 'push', rank: 40, vueComponent: 'agenda-user-push-settings'},
];

/**
 * The rank band whose contributed rows render INSIDE the "Your calendars"
 * section rather than between the page's own rows: the calendar-states (29)
 * and hidden-calendars (30) rows describe the calendars backing My Calendars,
 * so they belong under the account that materialises those calendars, above
 * the "Remote calendars" section that follows at rank 35.
 */
const NESTED_CALENDAR_RANK_MIN = 21;

const NESTED_CALENDAR_RANK_MAX = 34;

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
      return OWN_ROWS.concat(this.topLevelSections)
        .sort((a, b) => (a.rank || 0) - (b.rank || 0));
    },
    /**
     * The contributed rows that stay between the page's own rows: everything
     * outside the nested calendar band.
     *
     * @returns {Array} the rows to render at the top level
     */
    topLevelSections() {
      return this.sections
        .filter(section => !this.isNestedCalendarSection(section));
    },
    /**
     * The contributed rows that render inside the "Your calendars" section,
     * in rank order — today the calendar-states and hidden-calendars rows the
     * CalDAV add-on contributes.
     *
     * @returns {Array} the rows the section receives
     */
    nestedCalendarSections() {
      return this.sections
        .filter(section => this.isNestedCalendarSection(section))
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
    /**
     * Whether a contributed row belongs inside the "Your calendars" section
     * rather than at the top level of the page.
     *
     * @param {Object} section the contributed row descriptor
     * @returns {Boolean} true when its rank falls in the nested band
     */
    isNestedCalendarSection(section) {
      return (section.rank || 0) >= NESTED_CALENDAR_RANK_MIN
        && (section.rank || 0) <= NESTED_CALENDAR_RANK_MAX;
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
