SYSTEMD=/lib/systemd/system/
SERVICE=withings-reports.service
TIMER=withings-reports.timer

install: install start

install:
	sudo cp ${SERVICE} ${TIMER} ${SYSTEMD}

sudo systemctl daemon-reload

start:
	sudo systemctl start ${TIMER}

stop:
	sudo systemctl stop ${TIMER}

enable:
	sudo systemctl enable ${TIMER}

disable:
	sudo systemctl disable ${TIMER}
