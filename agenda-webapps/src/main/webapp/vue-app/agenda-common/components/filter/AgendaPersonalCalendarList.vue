<template>
  <div>
    <div v-if="loading && !calendars.length" class="d-flex justify-center py-2">
      <v-progress-circular
        color="primary"
        size="20"
        width="2"
        indeterminate />
    </div>
    <v-list
      v-else
      class="pa-0"
      dense>
      <v-list-item
        v-for="calendar in calendars"
        :key="calendar.id"
        class="agenda-calendar-settings px-0">
        <v-list-item-content
          :title="calendar.description || calendarLabel(calendar)"
          class="flex-grow-1 pa-0">
          <v-checkbox
            :input-value="isDisplayed(calendar)"
            :color="calendar.color"
            :label="calendarLabel(calendar)"
            class="agenda-calendar-settings-color ms-4"
            dense
            hide-details
            @change="toggle(calendar)" />
        </v-list-item-content>
        <!-- No action on a not-yet-persisted default calendar (id 0): it can
             only be edited once it exists, i.e. after the first event -->
        <!--
          Revealed on hover on a pointer device, always there on touch: three
          dots on every row turned a list of calendars into a column of
          controls. The class does the work in the panel's stylesheet, and
          focus-within keeps it reachable from the keyboard.
        -->
        <v-list-item-action
          v-if="calendar.id"
          class="my-0 ms-2 agenda-calendar-actions">
          <v-menu
            offset-y
            left>
            <template #activator="{ on, attrs }">
              <v-btn
                v-bind="attrs"
                v-on="on"
                :title="$t('agenda.calendar.actions')"
                icon
                x-small>
                <v-icon size="14">fa-ellipsis-v</v-icon>
              </v-btn>
            </template>
            <v-list dense class="pa-0">
              <v-list-item @click="editCalendar(calendar)">
                <v-list-item-title>{{ $t('agenda.calendar.edit') }}</v-list-item-title>
              </v-list-item>
              <v-list-item
                v-if="!calendar.system"
                @click="confirmDelete(calendar)">
                <v-list-item-title class="error--text">{{ $t('agenda.calendar.delete') }}</v-list-item-title>
              </v-list-item>
            </v-list>
          </v-menu>
        </v-list-item-action>
      </v-list-item>
    </v-list>
    <exo-confirm-dialog
      ref="deleteConfirmDialog"
      :title="$t('agenda.calendarDelete.confirmTitle')"
      :message="deleteConfirmMessage"
      :ok-label="$t('agenda.calendar.delete')"
      :cancel-label="$t('agenda.button.cancel')"
      @ok="deleteCalendar" />
  </div>
</template>

<script>
export default {
  data: () => ({
    calendars: [],
    hiddenCalendarIds: [],
    loading: false,
    calendarToDelete: null,
    connectorWarning: '',
  }),
  computed: {
    /**
     * The identity id of the current user, owner of every calendar listed
     * here.
     *
     * @returns {Number} current user identity id
     */
    userIdentityId() {
      return Number(eXo.env.portal.userIdentityId);
    },
    /**
     * The browser storage key holding the hidden calendar ids, scoped per
     * user so shared browsers don't leak one user's selection to another.
     *
     * @returns {String} local storage key
     */
    storageKey() {
      return `agenda.hiddenPersonalCalendars.${this.userIdentityId}`;
    },
    /**
     * The message of the deletion confirmation dialog, stating explicitly
     * that the events will be moved to the default calendar, never deleted.
     *
     * @returns {String} confirmation message for the calendar being deleted
     */
    deleteConfirmMessage() {
      if (!this.calendarToDelete) {
        return '';
      }
      const message = this.$t('agenda.calendarDelete.confirmMessage', {
        0: this.calendarLabel(this.calendarToDelete),
        1: this.defaultCalendarLabel,
      });
      // A connector mirroring this calendar elsewhere knows something agenda
      // does not: that confirming also destroys a copy on a remote server,
      // events other devices added included. Agenda cannot phrase that — it
      // does not know which server, or whether the copy is eXo's to delete —
      // so the connector supplies the sentence and agenda shows it. It is
      // fetched before the dialog opens, never while this is computed: the
      // answer lives on a server, and a warning that arrives after the user
      // has confirmed is no warning at all.
      return this.connectorWarning && `${message}\n\n${this.connectorWarning}` || message;
    },
    /**
     * The display label of the user's default calendar, used in the deletion
     * confirmation message as the destination of the moved events.
     *
     * @returns {String} default calendar label
     */
    defaultCalendarLabel() {
      const defaultCalendar = this.calendars.find(calendar => calendar.system);
      return defaultCalendar && this.calendarLabel(defaultCalendar) || this.$t('agenda.myCalendar');
    },
  },
  created() {
    this.hiddenCalendarIds = this.readHiddenCalendarIds();
    this.$root.$on('agenda-refresh-personal-calendars', this.retrieveCalendars);
    // Also on the document, so an add-on's drawer living in another Vue app —
    // the settings page has its own — can say that the set of personal
    // calendars just changed. A $root event never crosses that boundary.
    document.addEventListener('agenda-refresh-personal-calendars', this.retrieveCalendars);
    this.retrieveCalendars();
  },
  beforeDestroy() {
    this.$root.$off('agenda-refresh-personal-calendars', this.retrieveCalendars);
    document.removeEventListener('agenda-refresh-personal-calendars', this.retrieveCalendars);
  },
  methods: {
    /**
     * Retrieves the personal calendars of the current user. The REST list
     * endpoint returns a not-yet-persisted default calendar (id 0) when the
     * user never used the agenda: it is displayed the same way, and becomes a
     * real row on first use.
     *
     * @returns {Promise} resolved when the list is refreshed
     */
    retrieveCalendars() {
      this.loading = true;
      return this.$calendarService.getCalendars(0, 100, false, [this.userIdentityId])
        .then(data => {
          this.calendars = data && data.calendars || [];
          // The undeletable default calendar first, then by name
          this.calendars.sort((calendar1, calendar2) => (calendar2.system - calendar1.system)
            || this.calendarLabel(calendar1).localeCompare(this.calendarLabel(calendar2)));
        })
        .finally(() => this.loading = false);
    },
    /**
     * The display label of a calendar: its user-defined name when it has one,
     * else the localized 'My calendar' for the unnamed default — the server
     * keeps returning the owner display name, which is not a useful label for
     * one's own calendar.
     *
     * @param {Object} calendar the calendar to label
     * @returns {String} display label
     */
    calendarLabel(calendar) {
      if (calendar.name) {
        return calendar.name;
      }
      return calendar.system ? this.$t('agenda.myCalendar') : (calendar.title || this.$t('agenda.myCalendar'));
    },
    /**
     * Whether the events of a calendar are currently displayed in the agenda.
     *
     * @param {Object} calendar the calendar to check
     * @returns {Boolean} true when displayed
     */
    isDisplayed(calendar) {
      return this.hiddenCalendarIds.indexOf(Number(calendar.id)) < 0;
    },
    /**
     * Shows or hides the events of a calendar, persists the choice in the
     * browser storage and notifies the agenda so the grid filters
     * accordingly.
     *
     * @param {Object} calendar the calendar to toggle
     * @returns {void}
     */
    toggle(calendar) {
      const calendarId = Number(calendar.id);
      const index = this.hiddenCalendarIds.indexOf(calendarId);
      if (index < 0) {
        this.hiddenCalendarIds.push(calendarId);
      } else {
        this.hiddenCalendarIds.splice(index, 1);
      }
      localStorage.setItem(this.storageKey, JSON.stringify(this.hiddenCalendarIds));
      this.$root.$emit('agenda-personal-calendars-visibility-changed', this.hiddenCalendarIds.slice());
    },
    /**
     * Reads the hidden calendar ids persisted in the browser storage.
     *
     * @returns {Array} hidden calendar ids as numbers
     */
    readHiddenCalendarIds() {
      try {
        const storedValue = localStorage.getItem(this.storageKey);
        const hiddenIds = storedValue && JSON.parse(storedValue) || [];
        return Array.isArray(hiddenIds) ? hiddenIds.map(Number) : [];
      } catch (e) {
        return [];
      }
    },
    /**
     * Opens the calendar drawer pre-filled with the calendar to edit: name,
     * description and color are all edited there, in the same drawer that
     * creates calendars.
     *
     * @param {Object} calendar the calendar to edit
     * @returns {void}
     */
    editCalendar(calendar) {
      this.$root.$emit('agenda-personal-calendar-drawer-open', calendar);
    },
    /**
     * The connectors registered with agenda, in the shape they register
     * themselves.
     *
     * @returns {Array} the registered connectors, possibly empty
     */
    connectors() {
      return extensionRegistry.loadExtensions('agenda', 'connectors') || [];
    },

    /**
     * Asks every connector what deleting this calendar would also do, and
     * keeps the sentence the one that claims it wants shown.
     *
     * Asked once, before the dialog opens. A connector answering slowly delays
     * the dialog rather than letting it open without the warning — which is
     * the right trade: the whole point of the sentence is to be read before
     * the user confirms, not after.
     *
     * @param {Object} calendar the calendar about to be deleted
     * @returns {Promise} resolves once the warning is known
     */
    loadConnectorWarning(calendar) {
      this.connectorWarning = '';
      const connector = this.connectors().find(one => one && typeof one.describeCalendarDeletion === 'function');
      if (!connector) {
        return Promise.resolve();
      }
      return connector.describeCalendarDeletion(calendar)
        .then(description => {
          this.connectorWarning = description && description.claims && description.warning || '';
        })
        .catch(() => {
          // A connector that cannot answer must not stop the user deleting a
          // calendar. The dialog opens without its sentence, and the deletion
          // call itself still decides what happens remotely.
          this.connectorWarning = '';
        });
    },
    /**
     * Removes whatever a connector mirrors this calendar as, before agenda
     * removes the calendar itself.
     *
     * Resolves immediately when no connector claims it, which is every case
     * that existed before this hook.
     *
     * @param {Object} calendar the calendar being deleted
     * @returns {Promise} resolves once the remote side is gone, rejects to
     *          abort the whole deletion
     */
    deleteRemoteCounterpart(calendar) {
      const connector = this.connectors().find(one => one && typeof one.deleteCalendar === 'function');
      if (!connector) {
        return Promise.resolve();
      }
      return connector.deleteCalendar(calendar);
    },
    /**
     * Opens the deletion confirmation dialog for a calendar, stating that its
     * events will be moved to the default calendar.
     *
     * @param {Object} calendar the calendar to delete
     * @returns {void}
     */
    confirmDelete(calendar) {
      this.calendarToDelete = calendar;
      this.loadConnectorWarning(calendar).then(() => this.$refs.deleteConfirmDialog.open());
    },
    /**
     * Deletes the calendar confirmed by the user: server-side its events are
     * moved to the default calendar, so the agenda is refreshed afterwards to
     * show them under their new color.
     *
     * @returns {void}
     */
    deleteCalendar() {
      if (!this.calendarToDelete) {
        return;
      }
      const calendarId = this.calendarToDelete.id;
      const calendar = this.calendarToDelete;
      // The remote side first, and only then the local one. A connector that
      // fails here must leave BOTH sides untouched: deleting locally first can
      // strand a collection on a server after the record that knew about it is
      // gone, and nothing will ever find it again. So a rejection stops the
      // whole deletion rather than being reported after the fact.
      this.deleteRemoteCounterpart(calendar)
        .then(() => this.$calendarService.deleteCalendar(calendarId))
        .then(() => {
          this.calendarToDelete = null;
          return this.retrieveCalendars();
        })
        .then(() => this.$root.$emit('agenda-refresh'))
        // A connector that refused knows why, and agenda does not: which
        // server answered, and whether anything was deleted at all. It rejects
        // with a message already in the user's language, and that message is
        // shown rather than agenda's generic one — "nothing was deleted, in
        // eXo or on the server" is a very different thing to read than "the
        // calendar could not be deleted".
        .catch(error => this.$root.$emit('alert-message',
          error && error.message || this.$t('agenda.calendarDelete.error'),
          'error'));
    },
  },
};
</script>
