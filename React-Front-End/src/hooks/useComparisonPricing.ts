import { useEffect, useState } from "react";
import { calculateComparison, type ComparisonResponse } from "../services/pricing";
import type { ComparisonDeductible, RiskDetails } from "../types/quote";

function sameRiskDetails(left: RiskDetails, right: RiskDetails): boolean {
  return (
    left.driverAge === right.driverAge &&
    left.vehicleAge === right.vehicleAge &&
    left.annualMileage === right.annualMileage &&
    left.priorClaims === right.priorClaims
  );
}

export function useComparisonPricing(
  riskDetails: RiskDetails | null,
  deductible: ComparisonDeductible,
) {
  const [response, setResponse] = useState<ComparisonResponse | null>(null);
  const [requestError, setRequestError] = useState<{
    key: string;
    attempt: number;
    message: string;
  } | null>(null);
  const [retryAttempt, setRetryAttempt] = useState(0);
  const requestKey = riskDetails
    ? JSON.stringify([riskDetails, deductible])
    : "";

  useEffect(() => {
    if (!riskDetails) return;

    const controller = new AbortController();
    let active = true;

    calculateComparison(riskDetails, deductible, controller.signal)
      .then((nextResponse) => {
        if (!active) return;
        setResponse(nextResponse);
        setRequestError(null);
      })
      .catch((error: unknown) => {
        if (!active || controller.signal.aborted) return;
        setRequestError({
          key: requestKey,
          attempt: retryAttempt,
          message:
            error instanceof Error
              ? error.message
              : "We could not calculate this estimate. Please try again.",
        });
      });

    return () => {
      active = false;
      controller.abort();
    };
  }, [deductible, requestKey, retryAttempt, riskDetails]);

  const currentResponse =
    response &&
    response.comparison_deductible === deductible &&
    riskDetails &&
    sameRiskDetails(response.risk_inputs, riskDetails)
      ? response
      : null;
  const currentError =
    requestError?.key === requestKey && requestError.attempt === retryAttempt
      ? requestError.message
      : "";

  return {
    response: currentResponse,
    error: currentError,
    isLoading: !currentResponse && !currentError,
    retry: () => setRetryAttempt((attempt) => attempt + 1),
  };
}
