SYSTEMD=/lib/systemd/system/
SERVICE=withings-reports.service
TIMER=withings-reports.timer

install: install start

install: ${SERVICE} ${TIMER}
	sudo cp $? ${SYSTEMD}
	sudo systemctl daemon-reload

start:
	sudo systemctl start ${TIMER}

stop:
	sudo systemctl stop ${TIMER}

enable:
	sudo systemctl disable ${TIMER}
