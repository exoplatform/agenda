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
        class="calendar-owners-filters mx-4"
        @start-loading="$refs.calendarOwnersFilters.startLoading()"
        @end-loading="$refs.calendarOwnersFilters.endLoading()" />
    </template>
    <template slot="footer">
      <div class="d-flex">
        <v-btn
          :disabled="saving"
          class="btn ml-2"
          @click="close">
          {{ $t('agenda.button.cancel') }}
        </v-btn>
        <v-btn
          :loading="saving"
          :disabled="applyButtonDisabled"
          class="btn btn-primary ml-auto"
          @click="applyFilters">
          {{ $t('agenda.button.apply') }}
        </v-btn>
      </div>
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
    appliedOwnerIds: [],
  }),
  computed: {
    applyButtonDisabled() {
      return this.selectedOwnerIds === false || this.saving;
    },
    calendarOwnersSuggesterLabels() {
      return {
        placeholder: this.$t('agenda.filterAgendaPlaceholder'),
        noDataLabel: this.$t('agenda.noDataLabel'),
      };
    },
  },
  created() {
    this.$root.$on('agenda-calendar-owners-drawer-open', this.open);
  },
  methods: {
    applyFilters() {
      this.appliedOwnerIds = this.selectedOwnerIds;
      this.$emit('changed', this.selectedOwnerIds);
      this.close();
    },
    close() {
      this.$refs.calendarOwnersFilters.close();
    },
    open() {
      this.selectedOwnerIds = this.appliedOwnerIds;
      this.$refs.calendarOwnersFilters.open();
      this.$nextTick().then(() => {
        this.$refs.filterCalendarList.reset();
      });
    },
  },
};
</script>