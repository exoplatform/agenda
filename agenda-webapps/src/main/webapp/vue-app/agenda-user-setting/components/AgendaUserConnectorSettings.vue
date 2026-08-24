<template>
  <div>
    <!--
      The "Your calendars" section: the single CalDAV account backing
      My Calendars. Remote accounts (Google, Office 365) live in the
      "Remote calendars" section below — the two are different things to the
      user: this one IS their eXo calendars, the others are copies beside them.
    -->
    <v-list-item>
      <v-list-item-content>
        <!-- text-color, the class the E-mail rows on this same page use:
             text-header renders grey and lighter, so the calendar rows read
             as a different kind of setting than the ones above them. -->
        <v-list-item-title class="text-color">
          {{ $t('agenda.settings.yourCalendars') }}
        </v-list-item-title>
        <v-list-item-subtitle class="d-flex align-center">
          <template v-if="caldavKnown">
            <span v-if="caldavConnector.connected" class="text-truncate">
              {{ caldavConnector.user }}
            </span>
            <span v-else>
              {{ $t('agenda.settings.yourCalendarsNotConnected') }}
            </span>
          </template>
          <agenda-connector-status v-else :connectors="connectors">
            <template slot="connectButton">
              <span>
                {{ $t('agenda.connectYourPersonalAgendaSubTitle') }}
              </span>
            </template>
          </agenda-connector-status>
          <!--
            On the account's own line, after a separator: what makes the action
            beside it worth pressing is knowing how stale the account is, and
            reading it as a second line detached it from the account it is
            about. Shown only once the connector has answered, and only for one
            that can answer at all.
          -->
          <span v-if="lastSyncLabel" class="ms-1 text-truncate">
            · {{ lastSyncLabel }}
          </span>
        </v-list-item-subtitle>
      </v-list-item-content>
      <!--
        Both icons at one size and one weight, on one row: the pencil used to
        be an eXo icon font glyph in the primary colour while everything else
        on this settings page is a grey Vuetify icon, so it sat higher than its
        neighbour and read as the only thing worth clicking.
      -->
      <v-list-item-action class="d-flex flex-row align-center">
        <v-btn
          v-if="syncableConnector"
          :loading="syncing"
          :disabled="syncing"
          :aria-label="$t('agenda.connectors.syncNow')"
          :title="$t('agenda.connectors.syncNow')"
          icon
          class="me-2"
          @click="syncNow">
          <v-icon size="20" class="icon-default-color">fa-sync-alt</v-icon>
        </v-btn>
        <v-btn
          :aria-label="$t('agenda.settings.yourCalendars')"
          :title="$t('agenda.settings.yourCalendars')"
          icon
          @click="openDrawer">
          <v-icon size="20" class="icon-default-color">fa-edit</v-icon>
        </v-btn>
      </v-list-item-action>
    </v-list-item>
    <!--
      The rows the CalDAV add-on contributes about the calendars backing
      My Calendars — calendar states, hidden calendars — rendered inside this
      section: they describe what the account above materialises, so they
      belong under it, not floating between unrelated rows.
    -->
    <component
      :is="row.vueComponent"
      v-for="row in nestedSections"
      :key="row.id"
      :settings="settings"
      :connectors="connectors" />
    <agenda-connectors-drawer :connectors="enabledConnectors" />
    <agenda-connector
      :settings="settings"
      :connectors="connectors"
      auto-connect
      @connectors-loaded="connectorsLoaded" />
  </div>
</template>

<script>
export default {
  props: {
    settings: {
      type: Object,
      default: () => null,
    },
    /**
     * The contributed rows this section hosts, handed down by the page: the
     * calendar-states and hidden-calendars rows the CalDAV add-on registers
     * in the nested rank band.
     */
    nestedSections: {
      type: Array,
      default: () => [],
    },
  },
  data: () => ({
    connectors: [],
    syncing: false,
    syncStateRead: false,
    lastSynchronisedAt: null,
  }),
  computed: {
    enabledConnectors() {
      return this.connectors && this.connectors.filter(connector => connector.enabled) || [];
    },
    /**
     * The connector holding the CalDAV account backing My Calendars,
     * recognised by the `isCaldav` constant its descriptor declares.
     *
     * @returns {Object} the connector, or null while the flag is unknown
     */
    caldavConnector() {
      return this.enabledConnectors.find(connector => connector.isCaldav === true) || null;
    },
    /**
     * Whether a CalDAV connector can be told apart at all. A deployment whose
     * CalDAV descriptor predates the `isCaldav` constant cannot be split, so
     * this section falls back to the generic account line it always showed
     * rather than claiming there is no CalDAV account.
     *
     * @returns {Boolean} true when the CalDAV connector is recognisable
     */
    caldavKnown() {
      return !!this.caldavConnector;
    },
    /**
     * The account this section synchronises on demand: the CalDAV one when it
     * is recognisable and connected, else — on the legacy fallback — the
     * first connected connector able to.
     *
     * Declared by the connector, not assumed: a calendar whose events arrive
     * by push has nothing to run, and a button that does nothing is worse
     * than no button.
     *
     * @returns {Object} the connector, or null when none can
     */
    syncableConnector() {
      const candidates = this.caldavKnown ? [this.caldavConnector] : (this.connectors || []);
      return candidates
        .find(connector => connector
          && connector.connected
          && typeof connector.sync === 'function') || null;
    },
    /**
     * When the section's account last finished synchronising, in words.
     *
     * @returns {String} the line to display, empty while unknown
     */
    lastSyncLabel() {
      if (!this.syncStateRead) {
        return '';
      }
      const phrase = this.$remoteEventConnector.lastSyncPhrase(this.lastSynchronisedAt);
      return phrase && this.$t(phrase.key, {0: phrase.count}) || '';
    },
  },
  methods: {
    /**
     * Hands the loaded connectors to the page and reads their state.
     *
     * @param {Array} connectors the connectors the connector component built
     * @returns {void}
     */
    connectorsLoaded(connectors) {
      this.connectors = connectors;
      this.$emit('connectors-loaded', connectors);
      this.retrieveSyncState();
    },
    /**
     * Reads when the section's account last synchronised.
     *
     * A connector that fails to answer leaves the line absent rather than
     * showing a time that is not true.
     *
     * @returns {Promise} resolves once the connector has answered or failed
     */
    retrieveSyncState() {
      const connector = this.syncableConnector
        && typeof this.syncableConnector.lastSynchronised === 'function'
        && this.syncableConnector || null;
      if (!connector) {
        return Promise.resolve();
      }
      return Promise.resolve(connector.lastSynchronised())
        .then(lastSync => {
          this.lastSynchronisedAt = lastSync || null;
          this.syncStateRead = true;
        })
        .catch(error => console.error('cannot read when the account last synchronised', error));
    },
    /**
     * Synchronises the section's account now.
     *
     * The state is read again afterwards: pressing the button and seeing the
     * line stay where it was is the one outcome that would make it look
     * broken.
     *
     * @returns {Promise} resolves once the synchronisation has run
     */
    syncNow() {
      const connector = this.syncableConnector;
      this.syncing = true;
      return Promise.resolve(connector.sync())
        .then(() => {
          this.$root.$emit('agenda-refresh');
          this.$root.$emit('agenda-settings-refresh');
          return this.retrieveSyncState();
        })
        .catch(error => {
          console.error('cannot synchronise the connected account', error);
          this.$root.$emit('alert-message', this.$t('agenda.connectors.syncError'), 'error');
        })
        .finally(() => this.syncing = false);
    },
    /**
     * Opens the connect drawer for this section's account: scoped to the
     * CalDAV connector when it is recognisable, unfiltered on the legacy
     * fallback.
     *
     * @returns {void}
     */
    openDrawer() {
      this.$root.$emit('agenda-connectors-drawer-open', this.caldavKnown && {filter: 'caldav'} || null);
    },
    /**
     * Resolves a day abbreviation into the localised day name.
     *
     * @param {String} day the day abbreviation
     * @returns {String} the localised day name
     */
    getDayFromAbbreviation(day) {
      return this.$agendaUtils.getDayNameFromDayAbbreviation(day, eXo.env.portal.language);
    },
  }
};
</script>
