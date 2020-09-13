<template>
  <exo-identity-suggester
    id="calendarOwnerAutocomplete"
    ref="calendarOwnerSuggester"
    v-model="calendarOwner"
    :labels="calendarSuggesterLabels"
    :include-users="false"
    name="calendarOwnerAutocomplete"
    class="user-suggester"
    include-spaces />
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
  },
  data() {
    return {
      calendarOwner: null,
    };
  },
  computed: {
    calendarSuggesterLabels() {
      return {
        placeholder: this.$t('agenda.chooseCalendar'),
        noDataLabel: this.$t('agenda.noDataLabel'),
      };
    },
  },
  watch: {
    calendarOwner() {
      this.resetCustomValidity();

      if (this.calendarOwner) {
        this.event.calendar.owner = {
          remoteId: this.calendarOwner.remoteId,
          providerId: this.calendarOwner.providerId,
        };
      } else {
        this.event.calendar.owner = null;
      }
    },
  },
  created() {
    this.$root.$on('agenda-event-form-opened', () => {
      this.$nextTick().then(() => this.reset());
    });
  },
  methods:{
    resetCustomValidity() {
      if (this.$refs.calendarOwnerSuggester) {
        this.$refs.calendarOwnerSuggester.$el.querySelector('input').setCustomValidity('');
      }
    },
    validateForm() {
      this.resetCustomValidity();

      if (this.$refs.calendarOwnerSuggester && !this.event.calendar.owner || !this.event.calendar.owner.id && !(this.event.calendar.owner.providerId && this.event.calendar.owner.remoteId)) {
        this.$refs.calendarOwnerSuggester.$el.querySelector('input').setCustomValidity(this.$t('agenda.message.missingSpaceName'));
      }
    },
    reset() {
      if (this.event.id || this.event.parent) { // In case of edit existing event
        this.calendarOwner = this.$suggesterService.convertIdentityToSuggesterItem(this.event.calendar.owner);

        if (this.$refs.calendarOwnerSuggester) {
          this.$refs.calendarOwnerSuggester.items = [this.calendarOwner];
        }
      } else { // In case of new event
        if (this.currentSpace) {
          this.calendarOwner = this.event.calendar.owner = {
            id: `space:${this.currentSpace.prettyName}`,
            remoteId: this.currentSpace.prettyName,
            providerId: 'space',
            profile: {
              avatarUrl: this.currentSpace.avatarUrl,
              fullName: this.currentSpace.displayName,
            },
          };
          if (this.$refs.calendarOwnerSuggester) {
            this.$refs.calendarOwnerSuggester.items = [this.calendarOwner];
          }
        } else {
          if (this.$refs.calendarOwnerSuggester) {
            this.$refs.calendarOwnerSuggester.items = [];
          }
          this.calendarOwner = {};
        }
      }
      this.$forceUpdate();
    },
  }
};
</script>