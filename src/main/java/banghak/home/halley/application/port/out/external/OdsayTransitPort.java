package banghak.home.halley.application.port.out.external;

import banghak.home.halley.domain.scoring.TransitResult;

public interface OdsayTransitPort {

    TransitResult findTransit(double startX, double startY, double endX, double endY);
}
