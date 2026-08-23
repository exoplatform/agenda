<template>
  <!--
    The section is drawn here rather than by the panel, and only once there is
    something in it: this component is the only thing that knows whether the
    connected account has a collection eXo is not already showing elsewhere,
    and an empty section titled "Remote" is a question the user cannot act on.

    It owns the whole section, header included, because the panel cannot hide
    one from the outside: v-show writes an inline display:none that Vuetify's
    d-flex utility overrides with !important, so the section stayed on screen
    however the count came out. Owning its own root is what makes the decision
    actually take effect.

    Nothing is drawn while loading either — a section that appears, empties
    and vanishes is worse than one that arrives when it has content.
  -->
  <section
    v-if="!loading && calendars.length"
    class="agenda-left-panel-section d-flex flex-column mb-5">
    <div class="agenda-left-panel-title text-sub-title">
      <span class="flex-grow-1">{{ $t('agenda.leftPanel.myCalendars') }}</span>
    </div>
    <!-- The panel's indent lives on this wrapper, not on the list: Vuetify's
         pa-0 is !important and would wipe it off the list itself. -->
    <div class="agenda-left-panel-calendars">
      <v-list
        class="pa-0"
        dense>
        <v-list-item
          v-for="calendar in calendars"
          :key="calendar.id"
          class="agenda-calendar-settings px-0">
          <v-list-item-content :title="calendar.name" class="flex-grow-1 pa-0">
            <v-checkbox
              :input-value="isDisplayed(calendar)"
              :color="calendar.color"
              :label="calendar.name"
              class="agenda-calendar-settings-color ms-4"
              dense
              hide-details
              @change="toggle(calendar)" />
          </v-list-item-content>
        </v-list-item>
      </v-list>
    </div>
  </section>
</template>

<script>
export default {
  props: {
    connectors: {
      type: Array,
      default: () => [],
    },
  },
  data: () => ({
    calendarsRequestId: 0,
    calendars: [],
    hiddenIds: [],
    loading: false,
  }),
  computed: {
    /**
     * The connectors that are connected and able to list calendars.
     *
     * `connected` is the runtime state and is what decides. `isSignedIn` is a
     * static property on the CalDAV descriptor, always true whether or not an
     * account is configured, so gating on it alone meant the section kept
     * asking after the user had disconnected — and the request went out with a
     * null username, drawing the browser's own credentials prompt over the
     * agenda.
     *
     * @returns {Array} the connectors worth asking
     */
    connectedConnectors() {
      return (this.connectors || []).filter(connector => connector
          && connector.canListCalendars
          && connector.connected
          && typeof connector.listCalendars === 'function');
    },
    /**
     * Whether an account is in the middle of connecting.
     *
     * An account is marked connected before its first synchronisation runs,
     * and only that synchronisation takes its collections in. Asking in
     * between gets a true answer to the wrong question: nothing has
     * materialised yet, so every collection on the account still counts as
     * one eXo is not holding, and the section shows the lot for the second it
     * takes the synchronisation to finish.
     *
     * @returns {Boolean} true while a connector is still connecting
     */
    connectorsConnecting() {
      return (this.connectors || []).some(connector => connector && connector.loading);
    },
  },
  watch: {
    /**
     * Reacts to an account being connected or disconnected while the agenda is
     * open, so the list fills or empties without a reload.
     * @returns {void}
     */
    connectedConnectors() {
      // Not mid-connect: the account is flagged connected before its first
      // synchronisation, and answering then paints collections that are about
      // to stop being remote. The connecting watcher below asks once it is
      // over, so nothing is lost by staying quiet here.
      if (this.connectorsConnecting) {
        return;
      }
      this.retrieveCalendars();
    },
    /**
     * Asks once connecting is over.
     *
     * Its own trigger rather than a reliance on the refresh the connect flow
     * emits: this list must fill even if that signal is not sent, and asking
     * twice costs one request while not asking at all leaves the section
     * empty for the life of the page.
     *
     * @param {Boolean} connecting whether an account is still connecting
     * @returns {void}
     */
    connectorsConnecting(connecting) {
      if (!connecting) {
        this.retrieveCalendars();
      }
    },
  },
  created() {
    this.retrieveCalendars();
    // The same signal the personal list listens to, because materialising a
    // collection changes both panels at once: it leaves this one and joins
    // that one. Listening on only one side is what let a calendar sit under
    // Remote while already being shown under Personal.
    this.$root.$on('agenda-refresh-personal-calendars', this.retrieveCalendars);
    // And on the agenda's general refresh, which is what actually covers the
    // case this list kept getting wrong. Everything else that refreshes it —
    // creation, the connected-accounts watcher, connecting, disconnecting —
    // happens around the account changing. None of them fires when a
    // synchronisation materialises a collection, and that is the moment a
    // calendar stops being remote.
    //
    // So a page that loaded while a collection was still unbound kept
    // offering it for the life of that page, however many times it was
    // synchronised afterwards, and a reload only reproduced the same stale
    // answer if it happened in the same window. agenda-refresh is emitted
    // after a synchronisation completes, among a dozen other places, so this
    // list now corrects itself on the signal that matters rather than only on
    // the ones that happen to be near it.
    this.$root.$on('agenda-refresh', this.retrieveCalendars);
  },
  beforeDestroy() {
    this.$root.$off('agenda-refresh-personal-calendars', this.retrieveCalendars);
    this.$root.$off('agenda-refresh', this.retrieveCalendars);
  },
  methods: {
    /**
     * Records the collections to list.
     *
     * @param {Array} calendars the collections to show
     * @returns {void}
     */
    setCalendars(calendars) {
      this.calendars = calendars;
    },
    /**
     * Asks every connected connector able to answer for the calendars of the
     * account behind it, and renders them as one list.
     *
     * A connector that does not declare canListCalendars is skipped rather
     * than called, so the ones that predate this contract — Office 365 and
     * Outlook Exchange — are untouched. A connector that fails is logged and
     * dropped: one unreachable account must not empty the whole section.
     *
     * @returns {void}
     */
    retrieveCalendars() {
      const connectors = this.connectedConnectors;
      if (!connectors.length) {
        this.setCalendars([]);
        return;
      }
      this.loading = true;
      // Connecting fires two of these in quick succession — one the moment the
      // account is marked connected, one once its first synchronisation has
      // finished — and they answer different things: before the collections
      // are bound, every one of them is remote. Landing out of order, the
      // first overwrites the second and the panel keeps offering a calendar
      // that is already in the user's list, until the page is reloaded.
      //
      // The same guard the events grid uses, for the same reason: only the
      // newest request may write.
      const requestId = ++this.calendarsRequestId;
      Promise.all(connectors.map(connector =>
        connector.listCalendars()
          // The calendar eXo writes its copies to is left out of the list: it
          // holds nothing but duplicates of events the agenda already shows,
          // so displaying it would double every meeting on the grid.
          .then(calendars => this.$remoteEventConnector.excludeMirrorCalendar(connector, calendars))
          .catch(error => {
            console.error(`cannot list the calendars of ${connector.name}`, error);
            return [];
          })
      )).then(lists => {
        if (requestId !== this.calendarsRequestId) {
          // A newer retrieval was started since: its answer is the one that
          // reflects the account as it now stands.
          return;
        }
        this.setCalendars(lists.flat());
      }).finally(() => {
        if (requestId === this.calendarsRequestId) {
          this.loading = false;
        }
      });
    },
    /**
     * Whether a calendar's events are currently shown. Calendars are displayed
     * unless deliberately hidden, so a newly appearing one shows by default
     * rather than staying invisible until noticed.
     *
     * @param {Object} calendar calendar as the connector described it
     * @returns {Boolean} true when its events are shown
     */
    isDisplayed(calendar) {
      return !this.hiddenIds.includes(calendar.id);
    },
    /**
     * Shows or hides one calendar and publishes the resulting hidden set.
     *
     * @param {Object} calendar calendar the user just toggled
     * @returns {void}
     */
    toggle(calendar) {
      this.hiddenIds = this.isDisplayed(calendar)
        ? this.hiddenIds.concat(calendar.id)
        : this.hiddenIds.filter(id => id !== calendar.id);
      this.$root.$emit('agenda-remote-calendars-changed', this.hiddenIds.slice());
    },
  },
};
</script>
