import {
    createContext,
    useContext,
    useState,
    type ReactNode,
} from "react";

import type {
    ComparisonDeductible,
    CoveragePackage,
    RiskDetails,
} from "../types/quote";

interface QuoteDraftContextType {
    riskDetails: RiskDetails | null; //null means user didnt submit form yet
    setRiskDetails: (details: RiskDetails | null) => void; //either save validated details or clear draft
    selectedPackage: CoveragePackage;
    setSelectedPackage: (coveragePackage: CoveragePackage) => void;
    comparisonDeductible: ComparisonDeductible;
    setComparisonDeductible: (deductible: ComparisonDeductible) => void;
}

/*createContext makes shared place where componenets access data (dont need to pass props down tree) */
const QuoteDraftContext = createContext<QuoteDraftContextType | undefined>(undefined);


export function QuoteDraftProvider({ /*makes risk details available to components wrapped inside it */
    children,
}: {
    children: ReactNode;
}) {
    const [riskDetails, setRiskDetails] = useState<RiskDetails | null>(null); /*useState stores risk details*/
    const [selectedPackage, setSelectedPackage] = useState<CoveragePackage>("STANDARD");
    const [comparisonDeductible, setComparisonDeductible] =
        useState<ComparisonDeductible>(1000);

    return (
        <QuoteDraftContext.Provider
            value={{
                riskDetails,
                setRiskDetails,
                selectedPackage,
                setSelectedPackage,
                comparisonDeductible,
                setComparisonDeductible,
            }}
        >
            {children}
        </QuoteDraftContext.Provider>
    );
}

export function useQuoteDraft() { /*custom hook to make accessing context easier*/
    const context = useContext(QuoteDraftContext);
    if (!context) {
        throw new Error("useQuoteDraft must be used within a QuoteDraftProvider");
    }
    return context;
}