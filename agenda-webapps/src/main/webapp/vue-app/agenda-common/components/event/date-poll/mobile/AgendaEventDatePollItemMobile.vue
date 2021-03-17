<template>
  <v-list>
    <v-list-item-group
      v-model="selected"
      color="primary">
      <v-list-item
        v-for="(dateOption, index) in dateOptions"
        :key="index"
        @click="$root.$emit('selected-date-option', dateOption, index)">
        <template v-slot:default="{ active }">
          <agenda-event-date-option-vote
            class="my-auto"
            :date-option="dateOption"
            :voter="currentUserVotes"
            :vote="currentUserVotes.dateOptionVotes[index]"
            :disabled="!isVoting"
            @change="changeVote(index, $event)" />
          <agenda-event-date-option-period-mobile
            :date-option="dateOption"
            :can-select="canSelectDate"
            class="text--primary my-auto flex-grow-1 ml-4" />
          <v-list-item-content
            class="text--primary my-auto flex-grow-0 flex-shrink-0 pr-1 date-poll-voters-avatar">
            <agenda-event-date-option-voter-mobile :date-option="dateOption" />
          </v-list-item-content>
          <div v-if="!active" class="my-auto">
            <v-btn
              :title="$t('agenda.finalDate')"
              icon
              right
              fab
              x-small>
              <v-icon color="grey lighten-1">fa-trophy</v-icon>
            </v-btn>
          </div>
          <div v-else-if="canSelectDate && active" class="my-auto">
            <v-btn
              :title="$t('agenda.finalDate')"
              icon
              right
              fab
              x-small>
              <v-icon color="#f8b441">fa-trophy</v-icon>
            </v-btn>
          </div>
          <div v-else>
            <v-btn
              :title="$t('agenda.finalDate')"
              icon
              right
              fab
              x-small>
              <v-icon color="grey lighten-1">fa-trophy</v-icon>
            </v-btn>
          </div>
        </template>
      </v-list-item>
    </v-list-item-group>
  </v-list>
</template>

<script>
export default {
  props: {
    dateOptions: {
      type: Array,
      default: () => null
    },
    event: {
      type: Object,
      default: () => null
    },
    currentUserVotes: {
      type: Object,
      default: () => null
    },
    isVoting: {
      type: Boolean,
      default: false
    },
  },
  data:() => ({
    selected: 0,
    currentUserId: Number(eXo.env.portal.userIdentityId),
  }),
  computed: {
    isCreator() {
      return this.event && this.event.creator && Number(this.event.creator.id) === this.currentUserId;
    },
    canSelectDate() {
      return this.isCreator && !this.isVoting;
    },
  },
  methods: {
    changeVote(index, vote) {
      this.currentUserVotes.dateOptionVotes[index] = vote;
      this.$forceUpdate();
      this.$emit('changed');
    },
  }
};
</script>