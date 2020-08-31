<template>
  <v-container ref="agendaEventForm" class="event-form">
    <div class="row">
      <div>
        <label class="float-left mt-5 mr-3 text-subtitle-1">Create</label>
        <input ref="eventTitle" :placeholder="$t('agenda.eventTitle')" type="text" name="title" class="ignore-vuetify-classes my-3" required />
      </div>
      <div>
        <span class="mt-5  ml-4 mr-4 text-subtitle-1 font-weight-bold">in</span>
        <input ref="selectInput2" type="text" name="name" class="ignore-vuetify-classes my-3" />
      </div>
    </div>
    <div class="row">
      <div class="col">
        <div class="row">
          <div>
            <v-icon size="18" class="mr-11">
              fas fa-map-marker-alt
            </v-icon>
            <input ref="locationEvent" :placeholder="$t('agenda.eventLocation')" type="text" name="locationEvent" class="ignore-vuetify-classes my-3 location-event" required />
          </div>
        </div>
        <div class="row ">
          <div>
            <v-list>
              <v-icon size="18" class="mr-11">
                fas fa-bell
              </v-icon>
              <v-list-item v-for="notifs in notifications" :key="notifs">
                <label class="float-left ml-4 mr-4">{{ $t('agenda.label.notifyUsers') }}</label>
                <input ref="timeNotification" type="text" name="timeNotification" class="ignore-vuetify-classes my-3 time-notification" />
                <select class="width-auto my-auto ml-4 pr-2 subtitle-1 ignore-vuetify-classes d-none d-sm-inline notification-date-option">
                  <option>{{ $t('agenda.option.minutes') }}</option>
                  <option>{{ $t('agenda.option.hours') }}</option>
                  <option>{{ $t('agenda.option.days') }}</option>
                  <option>{{ $t('agenda.option.weeks') }}</option>
                </select>
                <span class="ml-4">{{ $t('agenda.label.beforeStart') }}</span>
                <v-btn
                  color="grey"
                  icon
                  dark
                  @click="removeNotifUser(notifs.id)"
                >
                  <v-icon>
                    mdi-close
                  </v-icon>
                </v-btn>
              </v-list-item>
              <a class="text-subtitle-1 font-weight-regular add-notification-link" @click="addNotification">{{ $t('agenda.addNotification') }}</a>
            </v-list>
          </div>
        </div>
        <div class=" row ">
          <v-icon size="18" class="mr-11">
            fas fa-redo-alt
          </v-icon>
          <select class="width-auto my-auto pr-2 subtitle-1 ignore-vuetify-classes d-none d-sm-inline">
            <option>1</option>
            <option>2</option>
            <option>3</option>
            <option>4</option>
          </select>
        </div>

        <div class="row ">
          <v-icon size="18" class="mr-12">
            fas fa-file-alt
          </v-icon>
          <textarea
            ref="autoFocusInput1"
            :placeholder="$t('agenda.description')"
            type="text"
            name="description"
            rows="20"
            maxlength="2000"
            noresize
            class="ignore-vuetify-classes my-3 description-event-textarea"
          >
          </textarea>
        </div>

        <div class="row">
          <v-icon size="18" class="mr-11">
            fas fa-paperclip
          </v-icon>
          <a class="text-subtitle-1 font-weight-regular attach-file-link" @click="uploadFile">{{ $t('agenda.attachFile') }}</a>
        </div>
      </div>
      <span>
        <v-divider vertical />
      </span>
      <div class="col ml-5">
        <div class="row">
          <v-icon size="18" class="mr-2">
            fas fa-users
          </v-icon>
          <exo-identity-suggester
            ref="autoFocusInput3"
            v-model="invitedMembers"
            :labels="suggesterLabels"
            :disabled="savingUser"
            name="inviteMembers"
            type-of-relations="user_to_invite"
            class="ma-4 user-suggester"
            include-users
            include-spaces
            multiple
          />
        </div>
        <div class="row">
          <label class="switch-label-text mt-1 text-subtitle-1 font-weight-bold">{{ $t('agenda.modifyEventPermission') }}</label>
          <v-switch v-model="enablePermission" class="mt-0 ml-4" />
        </div>
        <div class="row font-weight-regular">
          {{ $t('agenda.modifyEventPermissionDescription') }}
        </div>
        <div class="row">
          <label class="switch-label-text mt-1 text-subtitle-1 font-weight-bold">{{ $t('agenda.enableInvitation') }}</label>
          <v-switch v-model="enableInvitation" class="mt-0 ml-4" />
        </div>
        <div class="row font-weight-regular">
          {{ $t('agenda.enableInvitationDescription') }}
        </div>
      </div>
      <div class="fileHidden" style="display:none">
        <input ref="uploadInput" class="file" name="file" type="file" multiple="multiple" style="display:none">
      </div>
    </div>
  </v-container>
</template>

<script>
export default {
  data() {
    return {
      savingUser: false,
      invitedMembers: [],
      notifications: [],
      nbNotif: 0,
      enablePermission: false,
      enableInvitation: false,
    };
  },

  computed: {
    suggesterLabels() {
      return {
        placeholder: this.$t('agenda.addParticipants'),
        noDataLabel: this.$t('peopleList.label.noDataLabel'),
      };
    }
  },
  watch: {
    savingUser() {
      if (this.savingUser) {
        this.$refs.agendaEventForm.startLoading();
      } else {
        this.$refs.agendaEventForm.endLoading();
      }
    },
  },
  methods:{
    addNotification() {
      this.notifications.push({
        id: this.nbNotif++
      });
    },
    removeNotifUser(index) {
      this.notifications = this.notifications.filter((n) => n.id !== index);
    },
    uploadFile(){
      this.$refs.uploadInput.click();
    },
  }
};
</script>