<template>
  <!--
    Space-only contexts (a space agenda portlet, or a timeline configured on
    selected spaces) keep the historical space suggester untouched: this
    wrapper only exists to reintroduce a personal destination in the personal
    agenda, where the stock form offered none.
  -->
  <agenda-event-form-calendar-owner
    v-if="spaceOnlyMode"
    ref="calendarOwner"
    :event="event"
    :current-space="currentSpace"
    :calendars="calendars"
    @initialized="$emit('initialized')" />
  <div
    v-else
    :class="rootLayoutClass"
    class="d-flex">
    <!--
      An outlined dense v-select, the platform idiom for a bordered select
      (email-connector contact phone type): unlike a native select it renders
      the per-calendar color dots and the divider before the space entry, and
      carries the same border as the fields around it instead of the
      browser's own focus ring.

      Inline mode mirrors the title input's my-3 so the stretched row leaves
      exactly 40px for the select: with mt-3 alone, align-center re-centers
      the 40px box in the 52px left over and sinks it 6px below its
      neighbours. Stacked mode keeps mt-3 only, preserving the drawer's 8px
      gap to the suggester below.
    -->
    <div
      :class="inline ? 'my-3 destination-row-field' : 'mt-3'"
      class="d-flex flex-row align-center">
      <v-select
        v-model="selectedValue"
        :items="destinationItems"
        :aria-label="$t('agenda.destination.label')"
        :menu-props="{bottom: true, offsetY: true}"
        class="agenda-event-form-destination flex-grow-1 pt-0 mt-0"
        outlined
        dense
        hide-details>
        <template #item="{ item }">
          <span class="text-truncate">
            <v-icon
              v-if="item.color"
              :color="item.color"
              size="12"
              class="me-2">
              fa-circle
            </v-icon>{{ item.text }}
          </span>
        </template>
        <template #selection="{ item }">
          <span class="text-truncate">
            <v-icon
              v-if="item.color"
              :color="item.color"
              size="12"
              class="me-2">
              fa-circle
            </v-icon>{{ item.text }}
          </span>
        </template>
      </v-select>
    </div>
    <agenda-event-form-calendar-owner
      v-if="spacesModeSelected"
      ref="calendarOwner"
      :event="event"
      :current-space="currentSpace"
      :calendars="calendars"
      :class="inline ? 'ms-4 destination-row-field' : 'mt-2'"
      @initialized="$emit('initialized')" />
  </div>
</template>

<script>
export default {
  props: {
    event: {
      type: Object,
      default: () => ({}),
    },
    currentSpace: {
      type: Object,
      default: () => null,
    },
    calendars: {
      type: Array,
      default: () => [],
    },
    inline: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    personalCalendars: [],
    selectedValue: null,
    initialized: false,
  }),
  computed: {
    /**
     * The layout classes of the component root: stacked mode is a plain
     * column; inline mode is a row that takes as many equal-width field
     * shares of the destination row as it holds fields (agenda.less), so
     * the title input and every field in here resolve to the same width.
     *
     * @returns {String} classes to apply on the root element
     */
    rootLayoutClass() {
      if (!this.inline) {
        return 'flex-column';
      }
      return this.spacesModeSelected ? 'flex-row destination-row-fields-2' : 'flex-row destination-row-field';
    },
    /**
     * Whether the destination is restricted to spaces: inside a space agenda
     * the space is fixed, and a timeline configured on selected spaces keeps
     * its space-only gating. Both keep the historical behavior byte for
     * byte.
     *
     * @returns {Boolean} true when only space destinations are offered
     */
    spaceOnlyMode() {
      return !!this.currentSpace
        || !!(this.$root.timelineSettings && this.$root.timelineSettings.agendaSource === 'selectedSpaces');
    },
    /**
     * Whether the user chose to file the event in a space, which swaps the
     * historical space suggester in: beside the select when the inline
     * layout is in effect, below it otherwise.
     *
     * @returns {Boolean} true when the space flow is selected
     */
    spacesModeSelected() {
      return this.selectedValue === 'spaces';
    },
    /**
     * The items of the destination select: the user's personal calendars
     * (color dot + name), then a divider, then the entry leading to the
     * space flow.
     *
     * @returns {Array} select items
     */
    destinationItems() {
      const items = this.personalCalendars.map(calendar => ({
        text: this.calendarLabel(calendar),
        value: `calendar-${calendar.id}`,
        color: calendar.color,
      }));
      items.push({divider: true});
      items.push({
        text: this.$t('agenda.destination.spaces'),
        value: 'spaces',
      });
      return items;
    },
    /**
     * The identity id of the current user.
     *
     * @returns {Number} current user identity id
     */
    userIdentityId() {
      return Number(eXo.env.portal.userIdentityId);
    },
  },
  watch: {
    /**
     * Applies the picked destination to the event payload. A personal
     * calendar writes the explicit calendar id (what the server-side
     * resolution consumes) plus the user owner block; the space entry clears
     * both so the space suggester drives the payload exactly as before.
     * @returns {void}
     */
    selectedValue() {
      if (!this.initialized || this.selectedValue === null) {
        return;
      }
      if (!this.event.calendar) {
        this.$set(this.event, 'calendar', {});
      }
      if (this.spacesModeSelected) {
        // Only clear the payload when leaving a personal destination: when a
        // space event is being edited, its owner block is what the space
        // suggester preselects from and must be preserved
        const owner = this.event.calendar.owner;
        const ownerIsUser = owner && (String(owner.id) === String(this.userIdentityId)
          || owner.providerId === 'organization');
        if (!owner || ownerIsUser) {
          this.$set(this.event.calendar, 'id', 0);
          this.$set(this.event.calendar, 'owner', null);
          this.$root.$emit('agenda-event-change-owner', null);
        }
      } else {
        const calendarId = Number(String(this.selectedValue).replace('calendar-', ''));
        this.$set(this.event.calendar, 'id', calendarId);
        this.$set(this.event.calendar, 'owner', {
          id: String(this.userIdentityId),
          providerId: 'organization',
          remoteId: eXo.env.portal.userName,
        });
        this.$root.$emit('agenda-event-change-owner', null);
      }
    },
  },
  created() {
    if (!this.spaceOnlyMode) {
      this.retrievePersonalCalendars();
    }
  },
  methods: {
    /**
     * Retrieves the user's personal calendars then preselects the event's
     * current destination: the event's calendar when editing a personal
     * event, the space flow when editing a space event, the default calendar
     * for a new event — creating a personal event stays zero extra clicks.
     *
     * @returns {Promise} resolved once the selection is initialized
     */
    retrievePersonalCalendars() {
      return this.$calendarService.getCalendars(0, 100, false, [this.userIdentityId])
        .then(data => {
          this.personalCalendars = data && data.calendars || [];
          this.personalCalendars.sort((calendar1, calendar2) => (calendar2.system - calendar1.system)
            || this.calendarLabel(calendar1).localeCompare(this.calendarLabel(calendar2)));
          this.initializeSelection();
        })
        .finally(() => {
          this.initialized = true;
          this.$emit('initialized');
        });
    },
    /**
     * Computes the initial selection from the event being created or edited.
     * @returns {void}
     */
    initializeSelection() {
      // Applying the initial selection also writes it to the payload (the
      // watcher below), so the save button validity reflects it immediately
      this.initialized = true;
      const owner = this.event && this.event.calendar && this.event.calendar.owner;
      const ownerIsUser = owner && (String(owner.id) === String(this.userIdentityId)
        || owner.providerId === 'organization' && owner.remoteId === eXo.env.portal.userName);
      if (owner && !ownerIsUser) {
        // Editing (or pre-filling) an event belonging to a space
        this.selectedValue = 'spaces';
      } else if (ownerIsUser && this.event.calendar.id
          && this.personalCalendars.some(calendar => Number(calendar.id) === Number(this.event.calendar.id))) {
        // Editing a personal event: keep it where it is filed
        this.selectedValue = `calendar-${Number(this.event.calendar.id)}`;
      } else {
        // New event: the default calendar, zero extra clicks
        const defaultCalendar = this.personalCalendars.find(calendar => calendar.system) || this.personalCalendars[0];
        this.selectedValue = defaultCalendar ? `calendar-${defaultCalendar.id}` : 'spaces';
      }
    },
    /**
     * The display label of a personal calendar: its name, else the localized
     * 'My calendar' for the unnamed default.
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
     * Clears any custom validity set by the space suggester. A personal
     * destination has nothing to clear.
     * @returns {void}
     */
    resetCustomValidity() {
      if (this.$refs.calendarOwner) {
        this.$refs.calendarOwner.resetCustomValidity();
      }
    },
    /**
     * Validates the destination: a picked personal calendar is always valid;
     * the space flow delegates to the historical suggester validation, which
     * requires a space to be chosen.
     * @returns {void}
     */
    validateForm() {
      if (this.$refs.calendarOwner) {
        this.$refs.calendarOwner.validateForm();
      }
    },
  },
};
</script>
