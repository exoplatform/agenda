<template>
  <aside class="agenda-left-panel border-box-sizing full-height d-flex flex-column">
    <!--
      The calendars of a connected remote account. The section is mounted
      whenever a connector could answer, but hides itself until one actually
      does: whether an account holds any calendar is only known once it has
      been asked, and an empty heading is worse than none.
    -->
    <section
      v-show="myCalendarsSectionAvailable"
      class="agenda-left-panel-section agenda-left-panel-my-calendars d-flex flex-column">
      <div class="agenda-left-panel-title text-sub-title">
        {{ $t('agenda.leftPanel.myCalendars') }}
      </div>
      <agenda-left-panel-remote-calendars
        v-if="hasConnectors"
        class="agenda-left-panel-calendars"
        @availability="myCalendarsSectionAvailable = $event" />
    </section>
    <!-- section: Spaces -->
    <section class="agenda-left-panel-section d-flex flex-column">
      <div class="agenda-left-panel-title text-sub-title">
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
    myCalendarsSectionAvailable: false,
  }),
  computed: {
    /**
     * Whether any connector could list calendars at all, which decides only
     * whether it is worth mounting the section and asking. Whether the section
     * is then shown is the answer to that question, not a prediction of it.
     *
     * @returns {Boolean} true when a signed-in connector implements the contract
     */
    hasConnectors() {
      const connectors = extensionRegistry.loadExtensions('agenda', 'connectors') || [];
      return connectors.some(connector => connector && connector.canListCalendars && connector.isSignedIn);
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
