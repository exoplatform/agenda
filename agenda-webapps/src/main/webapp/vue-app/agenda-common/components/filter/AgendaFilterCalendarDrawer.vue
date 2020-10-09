<template>
  <exo-drawer
    ref="calendarOwnersFilters"
    right
    @opened="drawer = true"
    @closed="drawer = false">
    <template slot="title">
      {{ $t('agenda.filterAgendaTitle') }}
    </template>
    <template slot="content">
      <agenda-filter-calendar-list
        ref="filterCalendarList"
        v-model="selectedOwnerIds"
        class="calendar-owners-filters mr-4"
        @start-loading="$refs.calendarOwnersFilters.startLoading()"
        @end-loading="$refs.calendarOwnersFilters.endLoading()" />
    </template>
  </exo-drawer>
</template>

<script>
export default {
  props: {
    ownerIds: {
      type: Array,
      default: () => [],
    },
  },
  data: () => ({
    drawer: false,
    selectedOwnerIds: [],
  }),
  computed: {
    calendarOwnersSuggesterLabels() {
      return {
        placeholder: this.$t('agenda.filterAgendaPlaceholder'),
        noDataLabel: this.$t('agenda.noDataLabel'),
      };
    },
  },
  watch: {
    selectedOwnerIds() {
      this.applyFilters();
    },
  },
  created() {
    this.$root.$on('agenda-calendar-owners-drawer-open', this.open);
  },
  methods: {
    applyFilters() {
      this.$emit('changed', this.selectedOwnerIds);
    },
    close() {
      this.$refs.calendarOwnersFilters.close();
    },
    open() {
      this.$refs.calendarOwnersFilters.open();
      this.$nextTick().then(() => {
        this.$refs.filterCalendarList.reset();
      });
    },
  },
};
</script>