<template>
  <v-list dense>
    <!-- full (drawer) header: select-all, search and selection type filter.
         In compact mode (left panel) no header line is displayed at all: the
         panel shows only one row per calendar under its section title -->
    <v-list-item v-if="!compact" class="agenda-calendar-settings px-0">
      <v-list-item-action class="me-2 ms-4">
        <v-checkbox
          v-model="selectAll"
          :indeterminate="partiallySelected"
          color="primary"
          class="agenda-calendar-settings-color ma-auto"
          @click="changeAllSelection" />
      </v-list-item-action>
      <v-list-item-content>
        <agenda-filter-calendar-search
          ref="queryInput"
          v-model="query"
          class="mb-0 mt-1" />
      </v-list-item-content>
      <v-list-item-action class="ms-2">
        <select
          v-model="selectionType"
          class="width-auto my-auto subtitle-1 ignore-vuetify-classes">
          <option value="all">{{ $t('agenda.all') }}</option>
          <option value="selected">{{ $t('agenda.selected') }}</option>
          <option value="nonSelected">{{ $t('agenda.nonSelected') }}</option>
        </select>
      </v-list-item-action>
    </v-list-item>
    <!-- compact (left panel) mode: honest states while the first retrieval is
         in flight or when it genuinely returned nothing -->
    <v-list-item v-if="compact && loading && !calendars.length" class="justify-center">
      <v-progress-circular
        indeterminate
        color="primary"
        size="24" />
    </v-list-item>
    <v-list-item v-else-if="compact && initialized && !loading && !calendars.length">
      <v-list-item-content class="text-sub-title text-truncate">
        {{ $t('agenda.leftPanel.noCalendars') }}
      </v-list-item-content>
    </v-list-item>
    <!-- Keyed on owner AND calendar: an owner is not a row identity. This
         list holds space calendars, and nothing stops a space owning more
         than one — two rows would then share a key, and Vue would reuse or
         drop one of them on re-render. The calendar id alone would not do
         either: a space with no calendar yet is served as an unsaved one,
         whose id is 0 for every such space. -->
    <agenda-filter-calendar-item
      v-for="calendar in filteredCalendars"
      :key="`${calendar.owner.id}-${calendar.id}`"
      :calendar="calendar"
      :owner-ids="spaceIdentityIds"
      :selected-owner-ids="value"
      @changeSelection="changeSelection" />

    <v-flex v-if="hasMore" class="agendaLoadMoreParent d-flex my-4 border-box-sizing">
      <v-btn
        :loading="loading"
        :disabled="loading"
        class="btn mx-auto"
        @click="loadMore">
        {{ $t('agenda.button.loadMore') }}
      </v-btn>
    </v-flex>
  </v-list>
</template>

<script>
export default {
  props: {
    value: {
      type: [Array, Boolean],
      default: () => [],
    },
    compact: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    spaces: [],
    calendars: [],
    selectionType: 'all',
    selectAll: true,
    loading: false,
    query: null,
    limit: 20,
    pageSize: 20,
    totalSize: 0,
    initialized: false,
  }),
  computed: {
    /**
     * Calendars to display depending on the chosen selection type filter.
     *
     * @returns {Array} calendars to render
     */
    filteredCalendars() {
      if (this.selectionType === 'selected') {
        return this.selectedCalendars;
      } else if (this.selectionType === 'nonSelected') {
        return this.unselectedCalendars;
      }
      return this.calendars;
    },
    /**
     * Whether the 'select all' checkbox must display an indeterminate state:
     * some calendars are selected but not all of them.
     *
     * @returns {boolean} true when the selection is partial
     */
    partiallySelected() {
      return !!(Array.isArray(this.value) && this.value.length && (this.hasMore || this.spaceIdentityIds.length !== this.value.length));
    },
    /**
     * Whether more space calendars can be loaded from the server.
     *
     * @returns {boolean} true when a next page exists
     */
    hasMore() {
      return this.limit < this.totalSize;
    },
    /**
     * Identity ids of the loaded spaces, used as calendar owner ids.
     *
     * @returns {Array} space identity ids as numbers
     */
    spaceIdentityIds() {
      return this.spaces && this.spaces.filter(space => space.identity && space.identity.id).map(space => Number(space.identity.id)) || [];
    },
    /**
     * Calendars currently selected: all of them when the selection is empty
     * (which means 'all'), none when the selection is false.
     *
     * @returns {Array} selected calendars
     */
    selectedCalendars() {
      if (this.value === false) {
        return [];
      }
      if (this.selectAll || !this.value || !this.value.length) {
        return this.calendars;
      }
      return this.calendars && this.calendars.filter(calendar => this.value.indexOf(Number(calendar.owner.id)) >= 0) || [];
    },
    /**
     * Calendars currently not selected.
     *
     * @returns {Array} unselected calendars
     */
    unselectedCalendars() {
      return this.calendars && this.calendars.filter(calendar => !this.selectedCalendars || !this.selectedCalendars.find(cal => cal.owner.id === calendar.owner.id)) || [];
    },
  },
  watch: {
    /**
     * Reloads the calendar list when the pagination limit grows.
     * @returns {void}
     */
    limit() {
      this.retrieveCalendars();
    },
    /**
     * Reloads the calendar list when the search query changes.
     * @returns {void}
     */
    query() {
      this.retrieveCalendars();
    },
    /**
     * Relays the loading state to the parent component.
     * @returns {void}
     */
    loading() {
      if (this.loading) {
        this.$emit('start-loading');
      } else {
        this.$emit('end-loading');
      }
    },
  },
  methods: {
    /**
     * Initializes the list state: loads the calendars when not loaded yet and
     * recomputes the 'select all' checkbox from the current selection value.
     *
     * @param {boolean} focus when true, focuses the search input (drawer
     *          usage); defaults to false to avoid stealing the focus when the
     *          list is displayed in the left panel
     * @returns {void}
     */
    reset(focus) {
      if (!this.calendars || !this.calendars.length) {
        this.retrieveCalendars();
      }
      this.selectAll = Array.isArray(this.value) && !this.value.length;
      if (focus) {
        window.setTimeout(() => {
          const input = this.$refs.queryInput && this.$refs.queryInput.$el.querySelector('input');
          if (input) {
            input.focus();
          }
        }, 200);
      }
    },
    /**
     * Loads the next page of space calendars.
     * @returns {void}
     */
    loadMore() {
      if (this.hasMore) {
        this.limit += this.pageSize;
      }
    },
    /**
     * Applies the 'select all' checkbox state to the whole selection.
     * @returns {void}
     */
    changeAllSelection() {
      if (this.selectAll) {
        this.checkAll();
      } else {
        this.uncheckAll();
      }
    },
    /**
     * Handles a single calendar (un)selection coming from a list item and
     * emits the new selection value, normalizing it to 'all' or 'none' when
     * the boundaries are reached.
     *
     * @param {Array|boolean} selectedOwnerIds new selection emitted by the
     *          item: an array of owner ids or false when nothing is selected
     * @returns {void}
     */
    changeSelection(selectedOwnerIds) {
      if (selectedOwnerIds === false || !selectedOwnerIds.length) {
        this.uncheckAll();
      } else if (!this.query && !this.hasMore && selectedOwnerIds.length === this.spaceIdentityIds.length) {
        this.checkAll();
      } else {
        this.selectAll = false;
        this.$emit('input', selectedOwnerIds);
      }
    },
    /**
     * Selects all calendars. Outside of a search, the canonical 'all selected'
     * value is an empty array so that newly loaded calendars are selected too.
     * @returns {void}
     */
    checkAll() {
      if (this.query) {
        this.selectAll = false;
        this.$emit('input', this.spaceIdentityIds.slice());
      } else {
        this.selectAll = true;
        this.$emit('input', []);
      }
    },
    /**
     * Unselects all calendars: the canonical 'none selected' value is false.
     * @returns {void}
     */
    uncheckAll() {
      this.selectAll = false;
      this.$emit('input', false);
    },
    /**
     * Retrieves the spaces the user is member of, then their calendars, and
     * refreshes the displayed list.
     *
     * @returns {Promise} resolved when the calendars are loaded
     */
    retrieveCalendars() {
      this.initialized = true;
      this.loading = true;
      return this.$spaceService.getSpaces(this.query, 0, this.limit, 'member', 'identity').then(data => {
        this.spaces = data && data.spaces || [];
        this.totalSize = data && data.size || 0;
        if (this.spaceIdentityIds.length) {
          return this.$calendarService.getCalendars(0, this.limit, false, this.spaceIdentityIds);
        }
      }).then(data => {
        this.calendars = data && data.calendars || [];
      }).finally(() => this.loading = false);
    },
  },
};
</script>
