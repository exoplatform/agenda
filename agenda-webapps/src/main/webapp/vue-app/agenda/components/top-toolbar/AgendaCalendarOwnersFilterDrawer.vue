<template>
  <exo-drawer
    ref="caledarOwnersFilters"
    right
    @opened="drawer = true"
    @closed="drawer = false">
    <template slot="title">
      {{ $t('agenda.filterAgendaTitle') }}
    </template>
    <template slot="content">
      <div class="mx-4">
        <v-switch
          v-model="allCalendars"
          :label="$t('agenda.allAgendas')"
          class="reverse" />
        <exo-identity-suggester
          v-if="drawer"
          ref="calendarOwners"
          v-model="selectedOwnerSuggesters"
          :labels="calendarOwnersSuggesterLabels"
          :include-users="false"
          :diabled="refreshing"
          :height="160"
          name="calendarOwnersAutocomplete"
          class="agenda-filter-suggester"
          include-spaces
          multiple />
      </div>
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
          :disabled="saving"
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
    allCalendars: true,
    selectedOwnerIds: [],
    selectedOwnerSuggesters: [],
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
    refreshing() {
      if (this.refreshing) {
        this.$refs.caledarOwnersFilters.startLoading();
      } else {
        this.$refs.caledarOwnersFilters.endLoading();
      }
    },
    allCalendars(newVal, oldVal) {
      if (!!newVal === !!oldVal) {
        return;
      }
      if (this.allCalendars) {
        this.selectedOwnerSuggesters = [];
      }
    },
    selectedOwnerSuggesters() {
      if (this.allCalendars && this.selectedOwnerSuggesters && this.selectedOwnerSuggesters.length) {
        this.allCalendars = false;
      }
      this.computeOwnerIds();
    },
  },
  created() {
    this.$root.$on('agenda-calendar-owners-drawer-open', this.open);
  },
  methods: {
    applyFilters() {
      this.$emit('changed', this.selectedOwnerIds);
      this.close();
    },
    close() {
      this.$refs.caledarOwnersFilters.close();
    },
    open() {
      this.allCalendars = !this.ownerIds || !this.ownerIds.length;
      this.$refs.caledarOwnersFilters.open();
      this.$nextTick().then(() => {
        this.refreshCalendarSuggester();
      });
    },
    refreshCalendarSuggester() {
      this.$refs.calendarOwners.clear();
      this.$refs.calendarOwners.items = [];
      this.selectedOwnerSuggesters = [];
      if (!this.ownerIds || !this.ownerIds.length) {
        return;
      }
      const promises = this.ownerIds.map(ownerId => {
        return this.$suggesterService.getSuggesterItemByIdentityId(ownerId)
          .then(suggesterItem => {
            if (suggesterItem) {
              this.selectedOwnerSuggesters.push(suggesterItem);
            }
          });
      });
      this.refreshing = true;
      return Promise.all(promises)
        .then(() => {
          this.$refs.calendarOwners.items = this.selectedOwnerSuggesters;
        })
        .finally(() => this.refreshing = false);
    },
    computeOwnerIds() {
      this.selectedOwnerIds = [];
      this.selectedOwnerSuggesters.forEach(ownerSuggester => {
        if (!ownerSuggester) {
          return;
        }
        this.$spaceService.getSpaceByPrettyName(ownerSuggester.remoteId, 'identity')
          .then(space => {
            if (space && space.identity && space.identity.id) {
              this.selectedOwnerIds.push(space.identity.id);
            }
          });
      });
    },
  },
};
</script>