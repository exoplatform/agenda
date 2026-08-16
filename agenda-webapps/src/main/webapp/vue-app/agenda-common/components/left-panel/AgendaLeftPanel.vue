<template>
  <aside class="agenda-left-panel border-box-sizing full-height d-flex flex-column">
    <!--
      The calendars of a connected remote account.
      The section is shown whether or not an account is connected: it is how a
      user discovers that connecting one is possible at all, and the button
      beside the title is the same control the toolbar carries, so both lead to
      the same drawer rather than to two ways of doing one thing.
    -->
    <section
      v-if="connectorsAvailable"
      class="agenda-left-panel-section agenda-left-panel-my-calendars d-flex flex-column">
      <div class="agenda-left-panel-title text-sub-title">
        <span class="flex-grow-1">{{ $t('agenda.leftPanel.myCalendars') }}</span>
        <agenda-connect-to-remote-button
          :connectors="connectors"
          :settings="settings"
          :show-default-remote-events="showDefaultRemoteEvents"
          height="24"
          width="24"
          size="14"
          class="flex-grow-0"
          :show-toggle-action="false" />
      </div>
      <agenda-left-panel-remote-calendars
        :connectors="connectors"
        class="agenda-left-panel-calendars" />
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
    connectors: {
      type: Array,
      default: () => [],
    },
    settings: {
      type: Object,
      default: null,
    },
    showDefaultRemoteEvents: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    calendarsLoaded: false,
  }),
  computed: {
    /**
     * Whether the remote calendar feature exists in this deployment at all,
     * which is what decides if the section is worth showing. Not whether an
     * account is connected: an empty section with a way to connect is how the
     * feature is discovered, and hiding it means a user who has never
     * connected never learns it is there.
     *
     * @returns {Boolean} true when a connector implements the calendar contract
     */
    connectorsAvailable() {
      const connectors = extensionRegistry.loadExtensions('agenda', 'connectors') || [];
      return connectors.some(connector => connector && connector.canListCalendars);
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
