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
          v-if="editedCalendarId !== calendar.id"
          :title="calendarLabel(calendar)"
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
        <v-list-item-content v-else class="flex-grow-1 pa-0 ms-4">
          <v-text-field
            ref="renameInput"
            v-model="editedCalendarName"
            :loading="saving"
            :placeholder="$t('agenda.calendar.namePlaceholder')"
            maxlength="200"
            dense
            hide-details
            autofocus
            @keyup.enter="renameCalendar(calendar)"
            @keyup.esc="cancelEdition" />
        </v-list-item-content>
        <!-- No action on a not-yet-persisted default calendar (id 0): it can
             only be renamed once it exists, i.e. after the first event -->
        <v-list-item-action
          v-if="calendar.id && editedCalendarId !== calendar.id"
          class="my-0 ms-2">
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
              <v-list-item @click="startEdition(calendar)">
                <v-list-item-title>{{ $t('agenda.calendar.rename') }}</v-list-item-title>
              </v-list-item>
              <v-list-item
                v-if="!calendar.system"
                @click="confirmDelete(calendar)">
                <v-list-item-title class="error--text">{{ $t('agenda.calendar.delete') }}</v-list-item-title>
              </v-list-item>
            </v-list>
          </v-menu>
        </v-list-item-action>
        <v-list-item-action v-else-if="editedCalendarId === calendar.id" class="my-0 ms-2">
          <v-btn
            :title="$t('agenda.button.save')"
            :loading="saving"
            icon
            x-small
            @click="renameCalendar(calendar)">
            <v-icon size="14" color="primary">fa-check</v-icon>
          </v-btn>
        </v-list-item-action>
      </v-list-item>
      <!-- Add a calendar -->
      <v-list-item v-if="!addingCalendar" class="px-0">
        <v-list-item-content class="pa-0">
          <v-btn
            class="ms-2 px-2 text-none justify-start"
            text
            small
            color="primary"
            @click="startAddition">
            <v-icon size="14" class="me-2">fa-plus</v-icon>
            {{ $t('agenda.calendar.addCalendar') }}
          </v-btn>
        </v-list-item-content>
      </v-list-item>
      <v-list-item v-else class="px-0">
        <v-list-item-content class="flex-grow-1 pa-0 ms-4">
          <v-text-field
            ref="addInput"
            v-model="newCalendarName"
            :loading="saving"
            :placeholder="$t('agenda.calendar.namePlaceholder')"
            maxlength="200"
            dense
            hide-details
            autofocus
            @keyup.enter="addCalendar"
            @keyup.esc="cancelAddition" />
        </v-list-item-content>
        <v-list-item-action class="my-0 ms-2">
          <v-btn
            :title="$t('agenda.button.save')"
            :loading="saving"
            icon
            x-small
            @click="addCalendar">
            <v-icon size="14" color="primary">fa-check</v-icon>
          </v-btn>
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
    saving: false,
    addingCalendar: false,
    newCalendarName: '',
    editedCalendarId: 0,
    editedCalendarName: '',
    calendarToDelete: null,
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
      return this.$t('agenda.calendarDelete.confirmMessage', {
        0: this.calendarLabel(this.calendarToDelete),
        1: this.defaultCalendarLabel,
      });
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
    this.retrieveCalendars();
  },
  beforeDestroy() {
    this.$root.$off('agenda-refresh-personal-calendars', this.retrieveCalendars);
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
     * Opens the inline creation field.
     * @returns {void}
     */
    startAddition() {
      this.newCalendarName = '';
      this.addingCalendar = true;
    },
    /**
     * Closes the inline creation field discarding its content.
     * @returns {void}
     */
    cancelAddition() {
      this.addingCalendar = false;
      this.newCalendarName = '';
    },
    /**
     * Creates a new personal calendar with the typed name; the color is
     * assigned server-side from the palette. Validation errors (duplicate or
     * too long name) surface as translated alert messages.
     *
     * @returns {void}
     */
    addCalendar() {
      const name = this.newCalendarName && this.newCalendarName.trim();
      if (!name || this.saving) {
        return;
      }
      this.saving = true;
      this.$calendarService.saveCalendar({
        name: name,
        owner: {
          id: String(this.userIdentityId),
        },
      })
        .then(() => {
          this.cancelAddition();
          return this.retrieveCalendars();
        })
        .catch(error => this.handleSaveError(error))
        .finally(() => this.saving = false);
    },
    /**
     * Opens the inline rename field on a calendar row.
     *
     * @param {Object} calendar the calendar to rename
     * @returns {void}
     */
    startEdition(calendar) {
      this.editedCalendarId = calendar.id;
      this.editedCalendarName = calendar.name || '';
    },
    /**
     * Closes the inline rename field discarding any change.
     * @returns {void}
     */
    cancelEdition() {
      this.editedCalendarId = 0;
      this.editedCalendarName = '';
    },
    /**
     * Renames a calendar with the typed name. Renaming the default calendar
     * to an empty name restores its localized 'My calendar' label (the name
     * is cleared server-side).
     *
     * @param {Object} calendar the calendar being renamed
     * @returns {void}
     */
    renameCalendar(calendar) {
      if (this.saving) {
        return;
      }
      const name = this.editedCalendarName && this.editedCalendarName.trim() || '';
      if (!name && !calendar.system) {
        // A user calendar without a name would be indistinguishable
        return;
      }
      this.saving = true;
      const updatedCalendar = Object.assign({}, calendar, {name: name});
      this.$calendarService.saveCalendar(updatedCalendar)
        .then(() => {
          this.cancelEdition();
          return this.retrieveCalendars();
        })
        .then(() => this.$root.$emit('agenda-refresh'))
        .catch(error => this.handleSaveError(error))
        .finally(() => this.saving = false);
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
      this.$refs.deleteConfirmDialog.open();
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
      this.saving = true;
      this.$calendarService.deleteCalendar(calendarId)
        .then(() => {
          this.calendarToDelete = null;
          return this.retrieveCalendars();
        })
        .then(() => this.$root.$emit('agenda-refresh'))
        .catch(() => this.$root.$emit('alert-message', this.$t('agenda.calendarDelete.error'), 'error'))
        .finally(() => this.saving = false);
    },
    /**
     * Maps a calendar save error to a translated alert: the server responds
     * with a message code for validation errors (duplicate name, name too
     * long), anything else falls back to a generic message.
     *
     * @param {Error} error error thrown by the calendar service
     * @returns {void}
     */
    handleSaveError(error) {
      const errorMessage = String(error && error.message || '');
      let messageKey = 'agenda.calendarSave.error';
      if (errorMessage.indexOf('agenda.calendarNameAlreadyExists') >= 0) {
        messageKey = 'agenda.calendarNameAlreadyExists';
      } else if (errorMessage.indexOf('agenda.calendarNameExceedsMaxLength') >= 0) {
        messageKey = 'agenda.calendarNameExceedsMaxLength';
      }
      this.$root.$emit('alert-message', this.$t(messageKey), 'error');
    },
  },
};
</script>
