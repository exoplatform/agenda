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
    <!--
      Only a real manager of the space gets the menu: its one entry manages the
      calendar's private iCal link, which the server refuses anyone else
      (EXO-90252). acl.canPublish is the server's own answer — a member holding
      the manager role, not a super-manager who is not one, which is why it is
      not acl.canEdit. No menu on a space calendar not saved yet (id 0): it has nothing
      a link could publish until it exists.
    -->
    <v-list-item-action
      v-if="canManageLink"
      class="my-0 ms-2 agenda-calendar-actions">
      <v-menu
        :value="isRowMenuOpen('menu')"
        content-class="agendaCalendarRowMenu"
        offset-y
        left
        @input="toggleRowMenu('menu', $event)">
        <template #activator="{ on, attrs }">
          <v-btn
            v-bind="attrs"
            :title="$t('agenda.calendar.actions')"
            icon
            x-small
            v-on="on">
            <v-icon size="14">fa-ellipsis-v</v-icon>
          </v-btn>
        </template>
        <v-list dense class="pa-0">
          <!--
            Publishing (EXO-90252): one entry naming the calendar's state and opening
            the drawer, which is where a calendar is unpublished.
          -->
          <v-list-item
            :class="`agenda-calendar-link-action agenda-calendar-link-state-${linkStateOf(calendar)}`"
            @click="openCalendarLink(calendar)">
            <v-list-item-title class="d-flex align-center">
              <v-icon
                v-if="linkStateOf(calendar) === 'published'"
                size="14"
                class="me-2 success--text">
                fas fa-check
              </v-icon>
              <v-icon
                v-else-if="linkStateOf(calendar) === 'stopped'"
                size="14"
                class="me-2 warning--text">
                fas fa-exclamation-triangle
              </v-icon>
              {{ publishMenuLabel(calendar) }}
            </v-list-item-title>
          </v-list-item>
        </v-list>
      </v-menu>
    </v-list-item-action>
    <!--
      A published calendar (EXO-90252) carries a small sign, so a calendar anyone
      with its link can read does not look like the others; the colour and the
      glyph tell a working link from one that stopped. At the end of the row,
      to the right of the menu, which keeps its place while hidden. Same alignment
      as the read-only lock of the Remote rows (EXO-90236), and the same
      wrapper carrying the role and the label, since Vuetify hides a v-icon with
      no click listener from assistive technology.
    -->
    <v-list-item-action
      v-if="linkStateOf(calendar) !== 'none'"
      class="my-0 ms-2 flex-grow-0 justify-center agenda-calendar-published-icon">
      <span
        :title="publishedTooltip(calendar)"
        :aria-label="publishedTooltip(calendar)"
        :class="`d-flex agenda-calendar-published-${linkStateOf(calendar)}`"
        role="img">
        <v-icon
          :class="linkStateOf(calendar) === 'published' ? 'text-light-color' : 'warning--text'"
          size="14">
          {{ linkStateOf(calendar) === 'published' ? 'fas fa-link' : 'fas fa-unlink' }}
        </v-icon>
      </span>
    </v-list-item-action>
  </v-list-item>
</template>

<script>
import calendarLinkMenuMixin from '../../js/CalendarLinkMenuMixin.js';
import calendarRowMenuMixin from '../../js/CalendarRowMenuMixin.js';

export default {
  mixins: [calendarLinkMenuMixin, calendarRowMenuMixin],
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
    /**
     * Whether the current user may publish this saved calendar as a private
     * iCal link: acl.canPublish, a real manager of the space — never a
     * super-manager who is not one, although they can edit the calendar.
     *
     * @returns {boolean} true for a saved calendar the user may publish
     */
    canManageLink() {
      return !!this.calendar && Number(this.calendar.id) > 0 && !!this.calendar.acl && !!this.calendar.acl.canPublish;
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
