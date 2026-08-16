<template>
  <v-list-item class="agenda-calendar-settings px-0">
    <v-list-item-content :title="calendarDisplayName" class="flex-grow-1 pa-0">
      <v-checkbox
        v-model="checked"
        :color="calendarColor"
        :label="calendarDisplayName"
        class="agenda-calendar-settings-color ms-4"
        @click="changeSelection" />
    </v-list-item-content>
    <v-list-item-action
      v-if="!$root.isMobile"
      :id="calendarMenuId"
      class="calendarSettingActions">
      <v-menu
        v-if="canEditCalendar"
        ref="menu"
        v-model="menu"
        :close-on-content-click="false"
        :content-class="calendarMenuId"
        bottom
        left>
        <template #activator="{ on, attrs }">
          <v-btn
            icon
            v-bind="attrs"
            v-on="on">
            <v-icon>mdi-dots-vertical</v-icon>
          </v-btn>
        </template>
        <v-card>
          <v-color-picker
            v-model="selectedCalendarColor"
            class="ma-2"
            hide-inputs
            flat />
          <v-card-actions>
            <v-spacer />
            <v-btn
              :disabled="saving"
              class="btn ms-2"
              @click="closeMenu">
              {{ $t('agenda.button.cancel') }}
            </v-btn>
            <v-btn
              :loading="saving"
              :disabled="saving"
              class="btn btn-primary ms-2"
              @click="applyColor">
              {{ $t('agenda.button.apply') }}
            </v-btn>
          </v-card-actions>
        </v-card>
      </v-menu>
    </v-list-item-action>
  </v-list-item>
</template>

<script>
export default {
  props: {
    calendar: {
      type: Object,
      default: null,
    },
    ownerIds: {
      type: Array,
      default: () => [],
    },
    selectedOwnerIds: {
      type: [Array, Boolean],
      default: () => [],
    },
  },
  data: () => ({
    selectedCalendarColor: null,
    saving: false,
    menu: false,
    checked: false,
  }),
  computed: {
    /**
     * Whether this calendar is selected: an empty array selection means 'all
     * calendars', a false selection means 'no calendar'.
     *
     * @returns {boolean} true when the calendar is selected
     */
    selected() {
      return this.selectedOwnerIds !== false && (!this.selectedOwnerIds.length || this.selectedOwnerIds.indexOf(this.calendarOwnerId) >= 0);
    },
    /**
     * Identity id of the calendar owner, as a number.
     *
     * @returns {number} calendar owner identity id
     */
    calendarOwnerId() {
      return Number(this.calendar.owner.id);
    },
    /**
     * Unique DOM id of the color picker menu of this calendar.
     *
     * @returns {string} menu element id
     */
    calendarMenuId() {
      return `settingsMenu${this.calendarOwnerId}`;
    },
    /**
     * Color of the calendar.
     *
     * @returns {string} calendar color
     */
    calendarColor() {
      return this.calendar.color;
    },
    /**
     * Whether the current user can edit the calendar, thus change its color.
     *
     * @returns {boolean} true when the calendar is editable
     */
    canEditCalendar() {
      return this.calendar && this.calendar.acl && this.calendar.acl.canEdit;
    },
    /**
     * Display name of the calendar owner (space or user profile).
     *
     * @returns {string} calendar display name
     */
    calendarDisplayName() {
      const owner = this.calendar.owner;
      const profile = owner.space || owner.profile;
      return profile.displayName || profile.fullname || profile.fullName;
    },
  },
  watch: {
    /**
     * Asks other items to close their color picker menu when this one opens.
     * @returns {void}
     */
    menu() {
      if (this.menu) {
        this.$root.$emit('agenda-filter-close-menu', this.calendarOwnerId);
      }
    },
    /**
     * Keeps the checkbox state in sync with the selection coming from the
     * parent list.
     * @returns {void}
     */
    selected() {
      this.checked = this.selected;
    },
  },
  created() {
    this.selectedCalendarColor = this.calendar.color;
    this.$root.$on('agenda-filter-close-menu', this.closeOtherMenu);
  },
  mounted() {
    this.checked = this.selected;
  },
  beforeDestroy() {
    this.$root.$off('agenda-filter-close-menu', this.closeOtherMenu);
  },
  methods: {
    /**
     * Closes the color picker menu of this item when another item opens its
     * own menu.
     *
     * @param {number} calendarOwnerId owner id of the calendar whose menu just
     *          opened
     * @returns {void}
     */
    closeOtherMenu(calendarOwnerId) {
      if (calendarOwnerId !== this.calendarOwnerId) {
        this.menu = false;
      }
    },
    /**
     * Toggles the selection of this calendar and emits the new selection to
     * the parent list, without mutating the received props: unchecking the
     * last selected calendar emits false ('no calendar'), while checking a
     * calendar when everything is selected first materializes the full list
     * of owner ids.
     * @returns {void}
     */
    changeSelection() {
      let newSelection;
      if (this.selected) {
        const currentSelection = Array.isArray(this.selectedOwnerIds) && this.selectedOwnerIds.length && this.selectedOwnerIds.slice() || this.ownerIds.slice();
        const index = currentSelection.indexOf(this.calendarOwnerId);
        if (index >= 0) {
          currentSelection.splice(index, 1);
        }
        newSelection = currentSelection.length && currentSelection || false;
      } else {
        newSelection = Array.isArray(this.selectedOwnerIds) && this.selectedOwnerIds.slice() || [];
        newSelection.push(this.calendarOwnerId);
      }
      this.$emit('changeSelection', newSelection);
    },
    /**
     * Resets the color picker to the current calendar color.
     * @returns {void}
     */
    reset() {
      this.selectedCalendarColor = this.calendar.color;
    },
    /**
     * Saves the color chosen in the color picker on the calendar and notifies
     * the application so displayed events switch to the new color.
     * @returns {void}
     */
    applyColor() {
      const calendarToSave = JSON.parse(JSON.stringify(this.calendar));
      calendarToSave.color = this.selectedCalendarColor;
      this.saving = true;
      this.$calendarService.saveCalendar(calendarToSave)
        .then(() => {
          this.calendar.color = this.selectedCalendarColor;
          this.$root.$emit('agenda-calendar-color-changed', this.calendar.id, this.calendarColor);
          this.closeMenu();
        })
        .finally(() => this.saving = false);
    },
    /**
     * Closes the color picker menu.
     * @returns {void}
     */
    closeMenu() {
      this.menu = false;
    },
  },
};
</script>
