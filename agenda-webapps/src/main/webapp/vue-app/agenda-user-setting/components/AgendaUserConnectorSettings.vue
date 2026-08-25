<template>
  <div v-if="displayed">
    <!--
      The "My Calendars" section: the single CalDAV account backing the
      My Calendars group of the agenda's left panel. It is named after what
      the user already sees every day rather than after the account, and the
      subtitle states the relationship — synced both ways — instead of
      leaving "yours" to imply it. Accounts whose calendars stay foreign
      (Google, Office 365) live in "Calendars from other accounts" below.
    -->
    <v-list-item>
      <v-list-item-content>
        <!-- text-color, the class the E-mail rows on this same page use:
             text-header renders grey and lighter, so the calendar rows read
             as a different kind of setting than the ones above them. -->
        <v-list-item-title class="text-color">
          {{ $t('agenda.settings.myCalendars') }}
        </v-list-item-title>
        <v-list-item-subtitle class="d-flex align-center">
          <span class="text-truncate">
            {{ $t('agenda.settings.myCalendarsSyncedWith', {0: caldavConnector.user}) }}
          </span>
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
          :aria-label="$t('agenda.settings.myCalendarsManage')"
          :title="$t('agenda.settings.myCalendarsManage')"
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
    /**
     * The connectors the page loaded. Held by the page and not by this
     * section: the section hides itself when no CalDAV account is connected,
     * and a hidden section cannot be what loads the list every other section
     * reads.
     */
    connectors: {
      type: Array,
      default: () => [],
    },
  },
  data: () => ({
    syncing: false,
    syncStateRead: false,
    lastSynchronisedAt: null,
  }),
  watch: {
    /**
     * Reads the account's sync state whenever the page hands down a new list.
     * The section used to do this from its own load callback, which it no
     * longer owns.
     *
     * @returns {void}
     */
    connectors() {
      this.retrieveSyncState();
    },
  },
  computed: {
    enabledConnectors() {
      return this.connectors && this.connectors.filter(connector => connector.enabled) || [];
    },
    /**
     * The connector holding the CalDAV account backing My Calendars,
     * recognised by the `isCaldav` constant its descriptor declares.
     *
     * A deployment declares several CalDAV servers — one embedded, others a
     * user may choose between — so several CalDAV connectors can be enabled
     * at once while the user holds an account on exactly one of them. Taking
     * the first would report on whichever server happens to be registered
     * first, and tell a user with a working BlueMind account that My
     * Calendars is not synced. The connected one wins; the first stands in
     * only when none is connected, so the section still has a row to offer
     * a connection from.
     *
     * @returns {Object} the connector, or null while the flag is unknown
     */
    caldavConnector() {
      const caldavConnectors = this.enabledConnectors.filter(connector => connector.isCaldav === true);
      return caldavConnectors.find(connector => connector.connected) || caldavConnectors[0] || null;
    },
    /**
     * Whether this section is worth showing at all.
     *
     * It describes one account and the calendars that account materialises,
     * so with no account connected there is nothing for it to describe: it
     * offered a connect prompt, and the rows nested under it — calendar
     * states, hidden calendars, the device setup — spoke about an account
     * that did not exist. Connecting a first account is offered by the
     * agenda application itself, where connecting is the task at hand.
     *
     * It also settles what the subtitle used to guess at. The account state
     * arrives asynchronously, so the section rendered before it knew, and a
     * user whose account was connected read "not synced" for a moment. Now
     * the section simply is not there until there is something true to say.
     *
     * @returns {Boolean} true when a CalDAV account is connected
     */
    displayed() {
      return !!this.caldavConnector && !!this.caldavConnector.connected;
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
