<template>
  <div v-if="displayed">
    <!--
      The "My Calendars" section: the single CalDAV account backing the
      My Calendars group of the agenda's left panel. It is named after what
      the user already sees every day rather than after the account, and the
      subtitle states the relationship — synced both ways — instead of
      leaving "yours" to imply it. Accounts whose calendars stay foreign
      (Google, Office 365) live in "Calendars from other accounts" below.

      This component is TWO things, and managed mode needs them apart: its own
      header line — the account, how stale it is, and the two buttons — and the
      container holding the rows the CalDAV add-on nests under it. Managed mode
      suppresses the header line only. Suppressing the container instead took
      away the nested rows as well, including the one telling a user how to
      point their phone at the account, which is if anything MORE useful once
      the instance chose the server for them.
    -->
    <v-list-item v-if="headerDisplayed">
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
        <!--
          Sync now stays for a managed user; the pencil opens the connectors
          drawer, which managed mode takes away (EXO-90836).
        -->
        <v-btn
          v-if="syncableConnector"
          :loading="syncing"
          :disabled="syncing"
          :aria-label="$t('agenda.connectors.syncNow')"
          :title="$t('agenda.connectors.syncNow')"
          :class="{'me-2': !caldavManaged}"
          icon
          @click="syncNow">
          <v-icon size="20" class="icon-default-color">fa-sync-alt</v-icon>
        </v-btn>
        <v-btn
          v-if="!caldavManaged"
          :aria-label="$t('agenda.settings.myCalendarsManage')"
          :title="$t('agenda.settings.myCalendarsManage')"
          icon
          @click="openDrawer">
          <v-icon size="20" class="icon-default-color">fa-edit</v-icon>
        </v-btn>
      </v-list-item-action>
    </v-list-item>
    <!--
      The same line before there is an account: what the section will describe
      once there is one, and the way to get one. It replaces the account line
      rather than joining it — one of the two is always the whole of what this
      section has to say about the account.
    -->
    <v-list-item v-else-if="connectOffered">
      <v-list-item-content>
        <v-list-item-title class="text-color">
          {{ $t('agenda.settings.myCalendars') }}
        </v-list-item-title>
        <v-list-item-subtitle class="text-truncate">
          {{ $t('agenda.settings.myCalendarsConnectPrompt') }}
        </v-list-item-subtitle>
      </v-list-item-content>
      <v-list-item-action>
        <v-btn
          :aria-label="$t('agenda.connect')"
          :loading="!!(managedConnector && managedConnector.loading)"
          class="btn"
          @click="connectAccount">
          <v-icon size="14" class="me-1">fa-plug</v-icon>
          {{ $t('agenda.connect') }}
        </v-btn>
      </v-list-item-action>
    </v-list-item>
    <!--
      A managed user whose account the designated server refused (EXO-91017):
      no connect button, which would be refused again, but why.
    -->
    <v-list-item v-else-if="managedRefused">
      <v-list-item-content>
        <v-list-item-title class="text-color">
          {{ $t('agenda.settings.myCalendars') }}
        </v-list-item-title>
        <v-list-item-subtitle class="text-wrap">
          {{ $t('agenda.settings.myCalendarsManagedRefused') }}
        </v-list-item-subtitle>
      </v-list-item-content>
    </v-list-item>
    <!--
      The rows the CalDAV add-on contributes about the calendars backing
      My Calendars — calendar states, hidden calendars — rendered inside this
      section: they describe what the account above materialises, so they
      belong under it, not floating between unrelated rows.

      Gated on the account and not on the container: every one of them speaks
      about a connected account — the device URL is built from its address, the
      hidden calendars are its own — so under the connect offer they would each
      describe an account that does not exist yet.
    -->
    <template v-if="connected">
      <component
        :is="row.vueComponent"
        v-for="row in nestedSections"
        :key="row.id"
        :settings="settings"
        :connectors="connectors" />
    </template>
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
     * Whether a CalDAV account backs My Calendars.
     *
     * <p>
     * It also settles what the subtitle used to guess at. The account state
     * arrives asynchronously, so the section rendered before it knew, and a
     * user whose account was connected read "not synced" for a moment. Nothing
     * is said about the account until there is something true to say.
     *
     * @returns {Boolean} true when a CalDAV account is connected
     */
    connected() {
      return !!this.caldavConnector && !!this.caldavConnector.connected;
    },
    /**
     * Whether this section offers connecting a first account.
     *
     * <p>
     * This page used to hide itself whole when no account was connected, on
     * the ground that connecting is offered by the agenda application, where
     * connecting is the task at hand rather than one preference among others.
     * That reasoning holds for where connecting is BEST offered and not for
     * where it may be found: the only affordance left was a plug icon beside
     * the "+" of the left panel's Personal header, and on a CalDAV-only
     * deployment this page — the page carrying the e-mail account rows, so the
     * page a user reaches with exactly this in mind — offered no way to
     * connect a calendar at all. A user who does not find it concludes the
     * feature is not there.
     *
     * <p>
     * In managed mode the instance chose the account: what is offered is
     * connecting that one, in one click, never the connectors drawer
     * (EXO-90836) - and nothing when no descriptor says which server it is,
     * or when that server refused the user's own account (EXO-91017).
     *
     * @returns {Boolean} true when connecting is this user's to do and they
     *          have not done it yet
     */
    connectOffered() {
      return this.caldavKnown && !this.connected
        && (!this.caldavManaged || (!!this.managedConnector && !this.managedConnector.managedRefused));
    },
    /**
     * Whether the designated server refused the account managed mode connected
     * for this user (EXO-91017): the section then says so in place of the
     * connect offer.
     *
     * @returns {Boolean} true when the refusal is what this section has to say
     */
    managedRefused() {
      return this.caldavKnown && !this.connected && this.caldavManaged
        && !!this.managedConnector && !!this.managedConnector.managedRefused;
    },
    /**
     * The CalDAV descriptor of the server managed mode keeps this user on.
     *
     * @returns {Object} the descriptor, null when managed mode does not govern
     *          the user or names no descriptor here
     */
    managedConnector() {
      return this.$remoteEventConnector.managedCaldavConnector(this.connectors);
    },
    /**
     * Whether this section is on the page at all.
     *
     * <p>
     * This flag gates the CONTAINER, and the container holds the rows the
     * CalDAV add-on nests under it — hidden calendars, device setup — which
     * have their own reasons to exist and are not this component's header
     * line. Taking the container away to remove the header took those with it,
     * device setup included, which is the row a managed user most needs: the
     * instance chose the server, they still have to point their phone at it.
     * Managed mode only removes the header line's pencil.
     *
     * <p>
     * A managed user with no account yet is offered the designated server in
     * one click, as `connectOffered` says, or told why not, as `managedRefused`
     * says.
     *
     * @returns {Boolean} true when the section has either an account to
     *          describe or a connection to offer
     */
    displayed() {
      return this.connected || this.connectOffered || this.managedRefused;
    },
    /**
     * Whether this section's OWN header line is shown: the account address,
     * how stale it is, Sync now and the manage pencil.
     *
     * <p>
     * Shown to a managed user too, with Sync now: synchronising is the one
     * thing managed mode leaves them (EXO-90836). Only the pencil goes, since
     * it opens the connectors drawer.
     *
     * @returns {Boolean} true when the account line belongs on the page
     */
    headerDisplayed() {
      return this.connected;
    },
    /**
     * Whether the instance chose this user's CalDAV server for them, read
     * through the one helper every screen reads it through.
     *
     * @returns {Boolean} true when the account is not this user's to manage
     */
    caldavManaged() {
      return this.$remoteEventConnector.isCaldavManaged(this.connectors);
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
     * Connects an account: the designated server in one click when managed mode
     * governs the user (EXO-90836), the connect drawer otherwise.
     *
     * @returns {void}
     */
    connectAccount() {
      if (this.caldavManaged) {
        this.$root.$emit('agenda-connector-connect', this.managedConnector);
      } else {
        this.openDrawer();
      }
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
