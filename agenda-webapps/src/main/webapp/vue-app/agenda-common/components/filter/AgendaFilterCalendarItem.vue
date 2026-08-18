<template>
  <v-list-item class="agenda-calendar-settings px-0">
    <v-list-item-content :title="calendarDisplayName" class="flex-grow-1 pa-0">
      <v-checkbox
        v-model="checked"
        :color="calendarColor"
        :label="calendarDisplayName"
        class="agenda-calendar-settings-color ms-4"
        dense
        hide-details
        @click="changeSelection" />
    </v-list-item-content>
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
     * Color of the calendar, used to paint the row checkbox. The color is
     * read-only here: it belongs to the space and is managed by the space
     * administrators elsewhere.
     *
     * @returns {string} calendar color
     */
    calendarColor() {
      return this.calendar.color;
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
     * Keeps the checkbox state in sync with the selection coming from the
     * parent list.
     * @returns {void}
     */
    selected() {
      this.checked = this.selected;
    },
  },
  mounted() {
    this.checked = this.selected;
  },
  methods: {
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
  },
};
</script>
