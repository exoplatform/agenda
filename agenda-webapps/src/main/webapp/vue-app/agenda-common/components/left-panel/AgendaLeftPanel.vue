<template>
  <aside class="agenda-left-panel border-box-sizing full-height d-flex flex-column">
    <!-- section: My Calendars (EXO-89382 slot) -->
    <!--
      Placeholder for the 'My Calendars' section: personal calendars coming from
      remote connectors (EXO-89382). The connector contract that lists a user's
      personal calendars does not exist yet, so this section is gated off and
      renders nothing today. EXO-89382 should:
        1. turn myCalendarsSectionAvailable into a real availability check,
        2. replace the empty <section> content with the connector calendar list,
        3. emit the selected calendar identifiers the same way the Spaces
           section does (through the 'agenda-calendar-owners-changed' root
           event) so Agenda.vue keeps a single selection path.
    -->
    <section
      v-if="myCalendarsSectionAvailable"
      class="agenda-left-panel-section agenda-left-panel-my-calendars"></section>
    <!-- section: Spaces -->
    <section class="agenda-left-panel-section d-flex flex-column">
      <div class="subtitle-1 font-weight-bold px-4 pt-4">
        {{ $t('agenda.leftPanel.spaces') }}
      </div>
      <agenda-filter-calendar-list
        ref="calendarList"
        :value="selectedOwnerIds"
        class="agenda-left-panel-calendars"
        compact
        @input="changeSelection" />
    </section>
  </aside>
</template>

<script>
export default {
  props: {
    selectedOwnerIds: {
      type: [Array, Boolean],
      default: () => [],
    },
    expanded: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    calendarsLoaded: false,
  }),
  computed: {
    /**
     * Availability flag of the 'My Calendars' connector section. Always false
     * until EXO-89382 delivers the connector calendar contract, so the slot
     * renders nothing today.
     *
     * @returns {boolean} false while EXO-89382 is not implemented
     */
    myCalendarsSectionAvailable() {
      return false;
    },
  },
  watch: {
    /**
     * Lazily loads the space calendar list the first time the panel becomes
     * visible, so a user who keeps the panel collapsed never pays the REST
     * calls behind it.
     * @returns {void}
     */
    expanded() {
      if (this.expanded && !this.calendarsLoaded) {
        this.loadCalendars();
      }
    },
  },
  mounted() {
    if (this.expanded) {
      this.loadCalendars();
    }
  },
  methods: {
    /**
     * Triggers the initial retrieval of the space calendars displayed in the
     * Spaces section, at most once per application load.
     * @returns {void}
     */
    loadCalendars() {
      this.calendarsLoaded = true;
      if (this.$refs.calendarList) {
        this.$refs.calendarList.reset();
      }
    },
    /**
     * Relays a calendar selection change to the Agenda application through the
     * shared root event, so desktop panel and mobile filter drawer use the
     * same selection path.
     *
     * @param {Array|boolean} selectedOwnerIds selected calendar owner
     *          identity ids: an empty array means 'all calendars', false means
     *          'no calendar'
     * @returns {void}
     */
    changeSelection(selectedOwnerIds) {
      this.$root.$emit('agenda-calendar-owners-changed', selectedOwnerIds);
    },
  },
};
</script>
